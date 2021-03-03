package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		//packet handline
		IPv4 payload = null;
		
		//Check if is IPv4 packet.If not, drop it
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4
				&& etherPacket.getPayload() instanceof IPv4
				&& payload != null){
			return;
		}
		payload = (IPv4)etherPacket.getPayload();
		
		//verify the checksum. If not correct, drop it
		//get the given checksum
		short givenChecksum = payload.getChecksum();
		
		//recompute the checksum
		payload.resetChecksum();
		payload.serialize();
		short newChecksum = payload.getChecksum();
		
		if(givenChecksum != newChecksum){
			return;
		}
		
		//decrease the TTL by 1. If result is 0, drop it
		byte ttl = 0;
		if((ttl = payload.getTtl()) <= (byte)1)
			return;
		ttl--;
		payload.setTtl(ttl);
		
		//check if the packet is desinated to the router by compare the destination
		//IP of the packet and the IPs of the router's interfaces
		int destinationIp = payload.getDestinationAddress();	
		for(Iface iface: this.interfaces.values()){
			int currIp = iface.getIpAddress();
			if(currIp == destinationIp)
				return;
		}
		
		//Packet forwarding
		RouteEntry routeEntry = routeTable.lookup(destinationIp);	
		if (routeEntry == null)
			return;
		
		//find the forwarding interface
		ArpEntry arpEntry = arpCache.lookup(destinationIp);
		String destinationMAC = arpEntry.getMac().toString();
		//set the MAC addresses for the frame
		etherPacket.setSourceMACAddress(etherPacket.getDestinationMAC().toString());
		etherPacket.setDestinationMACAddress(destinationMAC);	
		//re-serialize the frame
		payload.resetChecksum();	
		payload.serialize();
		etherPacket.setPayload(payload);
		this.sendPacket(etherPacket, routeEntry.getInterface());
		/********************************************************************/
	}
}
