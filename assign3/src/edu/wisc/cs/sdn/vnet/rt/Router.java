package edu.wisc.cs.sdn.vnet.rt;

import java.util.Iterator;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import edu.wisc.cs.sdn.vnet.sw.MACTableEntry;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.UDP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device implements Runnable {
  public static final byte COMMAND_REQUEST = 1;
  public static final byte COMMAND_RESPONSE = 2;
  public static short RIP_PORT = (short)520;
  public static String MULTICAST_RIP = "224.0.0.9";
  public static String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";
  public static int UNSOLICITED_RESPONSE_INTERVAL = 10000;
  
  private Thread responseThread;
  
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
   * 
   */
  public void runRip() {
    // Connect to directly reachable subnets - don't expire
    for (Iface iface : this.interfaces.values()) {
      int ifaceIp = iface.getIpAddress();
      int ifaceMask = iface.getSubnetMask();

      int destIp = ifaceIp & ifaceMask;

      routeTable.insert(destIp, 0, ifaceMask, iface);
    }

    System.out.println("Initialized route table");
    System.out.println("-------------------------------------------------");
    System.out.println(routeTable);
    System.out.println("-------------------------------------------------");
    
    // Initial RIPv2 request
    RIPv2 initRequestRip = new RIPv2();
    initRequestRip.setCommand(COMMAND_REQUEST);
    initRequestRip.serialize();
    UDP initRequestUdp = new UDP();
    initRequestUdp.setPayload(initRequestRip);
    initRequestUdp.setSourcePort(RIP_PORT);
    initRequestUdp.setDestinationPort(RIP_PORT);
    initRequestUdp.serialize();
    // Send out on each of this router's interfaces
    for (Iface iface : this.interfaces.values()) {
      IPv4 initRequestIPv4 = new IPv4();
      initRequestIPv4.setPayload(initRequestUdp);
      initRequestIPv4.setDestinationAddress(MULTICAST_RIP);
      initRequestIPv4.setSourceAddress(iface.getIpAddress()); // piazza@279
      initRequestIPv4.serialize();
      Ethernet initRequestEthernet = new Ethernet();
      initRequestEthernet.setPayload(initRequestIPv4);
      initRequestEthernet.setDestinationMACAddress(BROADCAST_MAC);
      initRequestEthernet.setSourceMACAddress(iface.getMacAddress().toBytes()); // piazza@279
      
      sendPacket(initRequestEthernet, iface);
    }
    
    this.responseThread = new Thread(this);
    responseThread.start();
  }
  
  /**
   * Send unsolicited response every thirty seconds
   * 
   */
  public void run()
  {
      while (true)
      {
          // Run every 10 seconds
          try 
          { Thread.sleep(UNSOLICITED_RESPONSE_INTERVAL); }
          catch (InterruptedException e) 
          { break; }
          
          // Send out unsolicited response
          for (Iface iface : this.interfaces.values()) {
            
          }
      }
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
    /* TODO: Handle packets */

    switch (etherPacket.getEtherType()) {
      case Ethernet.TYPE_IPv4:
        this.handleIpPacket(etherPacket, inIface);
        break;
      // Ignore all other packet types, for now
    }

    /********************************************************************/
  }

  private void handleIpPacket(Ethernet etherPacket, Iface inIface) {
    // Make sure it's an IP packet
    if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
      return;
    }

    // Get IP header
    IPv4 ipPacket = (IPv4) etherPacket.getPayload();
    System.out.println("Handle IP packet");

    // Verify checksum
    short origCksum = ipPacket.getChecksum();
    ipPacket.resetChecksum();
    byte[] serialized = ipPacket.serialize();
    ipPacket.deserialize(serialized, 0, serialized.length);
    short calcCksum = ipPacket.getChecksum();
    if (origCksum != calcCksum) {
      return;
    }

    // Check TTL
    ipPacket.setTtl((byte) (ipPacket.getTtl() - 1));
    if (0 == ipPacket.getTtl()) {
      return;
    }

    // Reset checksum now that TTL is decremented
    ipPacket.resetChecksum();

    // Check if packet is destined for one of router's interfaces
    for (Iface iface : this.interfaces.values()) {
      if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
        return;
      }
    }

    // Do route lookup and forward
    this.forwardIpPacket(etherPacket, inIface);
  }

  private void forwardIpPacket(Ethernet etherPacket, Iface inIface) {
    // Make sure it's an IP packet
    if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
      return;
    }
    System.out.println("Forward IP packet");

    // Get IP header
    IPv4 ipPacket = (IPv4) etherPacket.getPayload();
    int dstAddr = ipPacket.getDestinationAddress();

    // Find matching route table entry
    RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

    // If no entry matched, do nothing
    if (null == bestMatch) {
      return;
    }

    // Make sure we don't sent a packet back out the interface it came in
    Iface outIface = bestMatch.getInterface();
    if (outIface == inIface) {
      return;
    }

    // Set source MAC address in Ethernet header
    etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

    // If no gateway, then nextHop is IP destination
    int nextHop = bestMatch.getGatewayAddress();
    if (0 == nextHop) {
      nextHop = dstAddr;
    }

    // Set destination MAC address in Ethernet header
    ArpEntry arpEntry = this.arpCache.lookup(nextHop);
    if (null == arpEntry) {
      return;
    }
    etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

    this.sendPacket(etherPacket, outIface);
  }
  
}
