package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Creates and manages an current table of MAC addresses
 * @author jacob
 *
 */
class MacAddressTable extends Thread {
	private Hashtable<MACAddress, Iface> MACLookupTable;
	private ArrayList<MACAddressTime> MACTimes;
	Thread cleanupThread;
	
	public MacAddressTable() {
		MACLookupTable = new Hashtable<MACAddress, Iface>();
		MACTimes = new ArrayList<MACAddressTime>();
		cleanupThread = new Thread(this, "Cleanup");
		cleanupThread.start();
	}
	
	/**
	 * Main method for the cleanup thread
	 */
	public void run() {
		while(true) {
			try {
				// Waits 1 second
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			//System.out.println("**MAT Running Cleanup**");
			cleanUp();
		}
	}
	
	/**
	 * Gets the Iface for a MAC address
	 * @param mac 
	 * @return Iface
	 */
	public synchronized Iface getIface(MACAddress mac) {
		return MACLookupTable.get(mac);
	}
	
	public synchronized boolean exists(MACAddress mac) {
		if(MACLookupTable.get(mac) == null) {
			return false;
		} else {
			return true;
		}
	}
	
	/**
	 * Adds a MAC address to the table
	 * @param mac
	 * @param iface
	 */
	public synchronized void addMAC(MACAddress mac, Iface iface) {
		MACLookupTable.put(mac, iface);
		MACAddressTime addrTime = new MACAddressTime(mac);
		MACTimes.add(addrTime);
		//System.out.println("MAC ADDR ADDED: " + mac.toString());
	}
	
	/**
	 * Updates the removal time of a MAC address to 15 seconds
	 * from when the command is run
	 * @param mac
	 */
	public synchronized void updateMACTime(MACAddress mac) {
		if(MACLookupTable.get(mac) == null) {
			return;
		} else {
			for(MACAddressTime time: MACTimes) {
				if(time.getMAC().equals(mac)) {
					time.updateTimeout();
				}
			}
		}
	}
	
	/**
	 * Removes all stale MAC addresses from the table
	 */
	public synchronized void cleanUp() {
		long currTime = System.currentTimeMillis();
		for(int i = 0; i<MACTimes.size(); i++) {
			if (MACTimes.get(i).getTimeout() <= currTime) {
				//System.out.println("MAC ADDR REMOVED: " + MACTimes.get(i).getMAC().toString());
				MACLookupTable.remove(MACTimes.get(i).getMAC());
				MACTimes.remove(i);
				i--;
			}
		}
	}
}

/**
 * This class stores a MAC address and its removal time
 * @author jacob
 *
 */
class MACAddressTime {
	private long timeout;
	private MACAddress mac;
	
	public MACAddressTime(MACAddress mac) {
		this.mac = mac;
		updateTimeout();
	}
	
	/**
	 * @return timeout
	 */
	public long getTimeout() {
		return timeout;
	}
	
	/**
	 * @return MAC address
	 */
	public MACAddress getMAC() {
		return mac;
	}
	
	/**
	 * Updates the removal time to 15 seconds from now
	 */
	public void updateTimeout() {
		timeout = System.currentTimeMillis() + 15000;
	}
}

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	
	private MacAddressTable MACTable;
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
		//System.out.println("MAT Starting");
		MACTable = new MacAddressTable();
		//System.out.println("MAT Successfully Started");
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
		//System.out.println("Switch Start Packet");
		// Gets the source and destination mac addresses
		MACAddress source = etherPacket.getSourceMAC();
		MACAddress destination = etherPacket.getDestinationMAC();
		
		// Checks to make sure they are not null
		if(source == null || destination == null) {
			System.out.println("Error: Source/Destination MAC null");
			return;
		}
		
		if(destination.equals(source)) {
			System.out.println("*** -> Packet dropped - source and destination mac address are the same: " + 
				etherPacket.toString().replace("\n", "\n\t"));
			// Drop packet with same source and dest
			return;
		}
		
		
		// Checks if the source is a known address
		if(MACTable.exists(source)) {
			// Updates the removal time
			MACTable.updateMACTime(source);
		} else {
			// Adds the MAC address to the table
			//System.out.println("New source, adding to MAT");
			MACTable.addMAC(source, inIface);
		}
		
		// Checks if the destination is a known address
		if(MACTable.exists(destination)){
			// Sends it to the stored destination
			//System.out.println("Destination out interface found. Sending");
			System.out.println("*** -> Packet sent: " + 
				etherPacket.toString().replace("\n", "\n\t"));
			sendPacket(etherPacket, MACTable.getIface(destination));
		} else {
			// Broadcasts it out to all interfaces except for the source
			//System.out.println("No destination found. Broadcasting");
			System.out.println("*** -> Packet broadcasted: " + 
				etherPacket.toString().replace("\n", "\n\t"));
			interfaces.forEach((name, outIface) -> {
				if(!outIface.equals(inIface)) {
					sendPacket(etherPacket, outIface);
				}
			});
		}
		
		//System.out.println("----Packet Sent----");
		
		/********************************************************************/
	}
	
	public void destroy() {
		super.destroy();
		MACTable.interrupt();
	}
}
