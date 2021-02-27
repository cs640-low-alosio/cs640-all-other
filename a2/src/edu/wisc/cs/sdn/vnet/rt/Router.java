package edu.wisc.cs.sdn.vnet.rt;

import java.lang.invoke.MethodHandles.Lookup;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
  /** Routing table for the router */
  private RouteTable routeTable;

  /** ARP cache for the router */
  private ArpCache arpCache;

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Router(String host, DumpFile logfile) {
    super(host, logfile);
    this.routeTable = new RouteTable();
    this.arpCache = new ArpCache();
  }

  /**
   * @return routing table for the router
   */
  public RouteTable getRouteTable() {
    return this.routeTable;
  }

  /**
   * Load a new routing table from a file.
   * 
   * @param routeTableFile the name of the file containing the routing table
   */
  public void loadRouteTable(String routeTableFile) {
    if (!routeTable.load(routeTableFile, this)) {
      System.err.println("Error setting up routing table from file " + routeTableFile);
      System.exit(1);
    }

    System.out.println("Loaded static route table");
    System.out.println("-------------------------------------------------");
    System.out.print(this.routeTable.toString());
    System.out.println("-------------------------------------------------");
  }

  /**
   * Load a new ARP cache from a file.
   * 
   * @param arpCacheFile the name of the file containing the ARP cache
   */
  public void loadArpCache(String arpCacheFile) {
    if (!arpCache.load(arpCacheFile)) {
      System.err.println("Error setting up ARP cache from file " + arpCacheFile);
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
		/* TODO: Handle packets                                             */
		/********************************************************************/
		
		if (!(etherPacket.getPayload() instanceof IPv4)) {
		  return;
        }
		
		if (etherPacket.getEtherType() != 0x0800) {
          return;
        }
		
		IPv4 ipacket = (IPv4) etherPacket.getPayload();
		int destIp = ipacket.getDestinationAddress();
		System.out.println("DEBUG: destIp: " + destIp);
		
		RouteEntry bestMatchEntry = routeTable.lookup(destIp);
		if (bestMatchEntry == null ) { // if no entry matches, drop
		  return;
		}
		
		int arpLookupIp = bestMatchEntry.getGatewayAddress();
		if (arpLookupIp == 0) {
		  arpLookupIp = destIp;
		}
		System.out.println("DEBUG: arpLookupIp: " + arpLookupIp);
		
		ArpEntry outArpEntry = arpCache.lookup(arpLookupIp);
		if (outArpEntry == null) { // TODO: do we need this?
		  System.out.println("DEBUG: no arp entry found");
		  return;
		}
		
		MACAddress newDestMacAddr = outArpEntry.getMac();
		System.out.println("DEBUG: newDestMacAddr: " + newDestMacAddr);
		Iface outIface = bestMatchEntry.getInterface();
		MACAddress newSourceMacAddr = outIface.getMacAddress();
		System.out.println("DEBUG: newSourceMacAddr: " + newSourceMacAddr);
		
		etherPacket.setDestinationMACAddress(newDestMacAddr.toString());
		etherPacket.setSourceMACAddress(newSourceMacAddr.toString());
		
		sendPacket(etherPacket, outIface);

	}
}
