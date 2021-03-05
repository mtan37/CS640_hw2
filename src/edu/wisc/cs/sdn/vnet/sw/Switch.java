package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.ArrayList;
import java.util.Hashtable;

class MacAddressTable extends Thread {
	private Hashtable<MACAddress, Iface> MACLookupTable;
	private ArrayList<MACAddressTime> MACTimes;
	
	public void run() {
		MACLookupTable = new Hashtable<MACAddress, Iface>();
		MACTimes = new ArrayList<MACAddressTime>();
		while(true) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				e.printStackTrace();
				break;
			}
			System.out.println("**MAT Running Cleanup**");
			cleanUp();
		}
	}
	
	public synchronized Iface getIface(MACAddress mac) {
		for(int i = 0; i<MACTimes.size(); i++) {
			if(MACTimes.get(i).getMAC() == mac) {
				MACTimes.get(i).updateTimeout();
				break;
			}
		}
		return MACLookupTable.get(mac);
	}
	
	public synchronized boolean exists(MACAddress mac) {
		if(MACLookupTable.get(mac) == null) {
			return false;
		} else {
			return true;
		}
	}
	
	public synchronized void addMAC(MACAddress mac, Iface iface) {
		MACLookupTable.put(mac, iface);
		MACAddressTime addrTime = new MACAddressTime(mac);
		MACTimes.add(addrTime);
	}
	
	public synchronized void cleanUp() {
		long currTime = System.currentTimeMillis();
		for(int i = 0; i<MACTimes.size(); i++) {
			if (MACTimes.get(i).getTimeout() <= currTime) {
				MACLookupTable.remove(MACTimes.get(i).getMAC());
				MACTimes.remove(i);
				i--;
			}
		}
	}
}

class MACAddressTime {
	private long timeout;
	private MACAddress mac;
	public MACAddressTime(MACAddress mac) {
		this.mac = mac;
		updateTimeout();
	}
	
	public long getTimeout() {
		return timeout;
	}
	
	public MACAddress getMAC() {
		return mac;
	}
	
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
		System.out.println("MAT Starting");
		MACTable = new MacAddressTable();
		MACTable.run();
		System.out.println("MAT Successfully Started");
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
		System.out.println("Switch Start Packet");
		MACAddress source = etherPacket.getSourceMAC();
		if(!MACTable.exists(source)) {
			System.out.println("New source, adding to MAT");
			MACTable.addMAC(source, inIface);
		}
		
		MACAddress destination = etherPacket.getDestinationMAC();
		if(MACTable.exists(destination)){
			System.out.println("Destination out interface found. Sending");
			sendPacket(etherPacket, MACTable.getIface(destination));
		} else {
			System.out.println("No destination found. Broadcasting");
			interfaces.forEach((name, outIface) -> {
				sendPacket(etherPacket, outIface);
			});
		}
		
		System.out.println("----Packet Sent----");
		
		/********************************************************************/
	}
	
	public void destroy() {
		super.destroy();
		MACTable.interrupt();
	}
}
