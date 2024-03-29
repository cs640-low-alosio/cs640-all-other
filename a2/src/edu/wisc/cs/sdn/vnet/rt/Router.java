package edu.wisc.cs.sdn.vnet.rt;

import java.util.Set;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
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
   * 
   * @param etherPacket the Ethernet packet that was received
   * @param inIface     the interface on which the packet was received
   */
  public void handlePacket(Ethernet etherPacket, Iface inIface) {
    System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

    /********************************************************************/
    /* Handle packets */
    /********************************************************************/

    /********************************************************************/
    // Drop if not IPv4
    /********************************************************************/
    if (etherPacket.getEtherType() != 0x0800) {
      System.out.println("\t- not an IPv4 packet; dropping");
      return;
    }

    if (!(etherPacket.getPayload() instanceof IPv4)) {
      System.out.println("\t- not an IPv4 packet; dropping");
      return;
    }

    /********************************************************************/
    // Is IPv4, handle packet
    /********************************************************************/
    IPv4 ipacket = (IPv4) etherPacket.getPayload();
    int destIp = ipacket.getDestinationAddress();
    // System.out.println("\t- destIp: " + destIp);

    // Validate checksum
    short expChecksum = ipacket.getChecksum();
    // System.out.println("\t- expChecksum: " + expChecksum);
    ipacket.resetChecksum();
    // System.out.println("\t- reset checksum: " + ipacket.getChecksum());
    ipacket.serialize();
    short actChecksum = ipacket.getChecksum();
    // System.out.println("\t- ActChecksum: " + actChecksum);
    if (expChecksum != actChecksum) {
      System.out.println("\t- Checksum mismatch; dropping");
      return;
    }

    /********************************************************************/
    // Handle TTL
    /********************************************************************/
    byte ttl = ipacket.getTtl();
    ttl--;
    // System.out.println("\t- TTL: " + ttl);
    if (ttl == 0) {
      System.out.println("\t- TTL reached 0; dropping");
      return;
    }
    ipacket.setTtl(ttl);
    ipacket.setChecksum((short) 0);
    ipacket.serialize(); // recalculate checksum with new TTL
    // System.out.println("\t- calc new checksum: " + ipacket.getChecksum());

    /********************************************************************/
    // Make sure packet is not destined for the same router
    /********************************************************************/
    Set<String> faceSet = this.interfaces.keySet();
    for (String faceName : faceSet) {
      Iface face = this.interfaces.get(faceName);
      if (destIp == face.getIpAddress()) {
        System.out.println("\t- packet source same as dest; dropping");
        return;
      }
    }

    /********************************************************************/
    // Setup for sending
    // Determine gateway or destination IP addr
    /********************************************************************/
    RouteEntry bestRouteEntry = routeTable.lookup(destIp);
    if (bestRouteEntry == null) { // if no entry matches, drop
      System.out.println("\t- no matching entry in routing table for given dest IP; dropping");
      return;
    }
    int arpLookupIp = bestRouteEntry.getGatewayAddress();
    if (arpLookupIp == 0) {
      // System.out.println("\t- destination IP in current network, sending to dest IP address");
      arpLookupIp = destIp;
    }
    // System.out.println("\t- arpLookupIp: " + arpLookupIp);

    /********************************************************************/
    // Determine new outgoing iface, new dest MAC addr, and new source MAC addr
    /********************************************************************/
    ArpEntry outArpEntry = arpCache.lookup(arpLookupIp);
    if (outArpEntry == null) {
      System.out.println("\t- couldn't find arp entry for given IP; dropping");
      return;
    }
    MACAddress newDestMacAddr = outArpEntry.getMac();
    if (newDestMacAddr == null) {
      System.out.println("\t- couldn't find new dest MAC addr in arp table; dropping");
      return;
    }
    // System.out.println("\t- newDestMacAddr: " + newDestMacAddr);

    Iface outIface = bestRouteEntry.getInterface();
    if (outIface == null) {
      System.out.println("\t- couldn't find outgoing interface in route table entry; dropping");
      return;
    }
    // System.out.println("\t- outIface name: " + outIface.getName());

    MACAddress newSourceMacAddr = outIface.getMacAddress();
    if (newSourceMacAddr == null) {
      System.out.println("\t- couldn't find source MAC addr for given interface; dropping");
      return;
    }
    // System.out.println("\t- newSourceMacAddr: " + newSourceMacAddr);

    etherPacket.setDestinationMACAddress(newDestMacAddr.toString());
    etherPacket.setSourceMACAddress(newSourceMacAddr.toString());
    sendPacket(etherPacket, outIface);
  }
}
