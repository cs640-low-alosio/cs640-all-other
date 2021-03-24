package edu.wisc.cs.sdn.vnet.rt;

import java.util.List;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.RIPv2;
import net.floodlightcontroller.packet.RIPv2Entry;
import net.floodlightcontroller.packet.UDP;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device implements Runnable {
  public static String MULTICAST_RIP = "224.0.0.9";
  public static String BROADCAST_MAC = "FF:FF:FF:FF:FF:FF";
  public static int UNSOLICITED_RESPONSE_INTERVAL = 10000;

  private Thread responseThread;

  /** Routing table for the router */
  private RouteTable routeTable;

  /** ARP cache for the router */
  private ArpCache arpCache;

  /** Router uses RIP to for routing instead of a provided, static table */
  private boolean isRipRouter;

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Router(String host, DumpFile logfile) {
    super(host, logfile);
    this.routeTable = new RouteTable();
    this.arpCache = new ArpCache();
    this.isRipRouter = false;
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

  public void setRipRouterTrue() {
    this.isRipRouter = true;
  }

  /**
   * Start router with RIPv2
   * 
   * Gets called by Main when starting a router without "-r" parameter
   */
  public void runRip() {
    // Connect to directly reachable subnets - don't expire
    for (Iface iface : this.interfaces.values()) {
      int ifaceIp = iface.getIpAddress();
      int ifaceMask = iface.getSubnetMask();

      int destIp = ifaceIp & ifaceMask;

      // neighbors have cost of 1
      routeTable.insert(destIp, 0, ifaceMask, iface, 1);
    }

    System.out.println("-------------------------------------------------");
    System.out.println("Initialized route table");
    System.out.println("-------------------------------------------------");
    System.out.println(routeTable);
    System.out.println("-------------------------------------------------");

    // Initial RIPv2 request
    RIPv2 initRequestRip = new RIPv2();
    initRequestRip.setCommand(RIPv2.COMMAND_REQUEST);
    UDP initRequestUdp = new UDP();
    initRequestUdp.setPayload(initRequestRip);
    initRequestUdp.setSourcePort(UDP.RIP_PORT);
    initRequestUdp.setDestinationPort(UDP.RIP_PORT);
    // Send out on each of this router's interfaces
    for (Iface iface : this.interfaces.values()) {
      IPv4 initRequestIPv4 = new IPv4();
      initRequestIPv4.setPayload(initRequestUdp);
      initRequestIPv4.setDestinationAddress(MULTICAST_RIP);
      initRequestIPv4.setSourceAddress(iface.getIpAddress()); // piazza@279
      initRequestIPv4.setProtocol(IPv4.PROTOCOL_UDP);
      Ethernet initRequestEthernet = new Ethernet();
      initRequestEthernet.setPayload(initRequestIPv4);
      initRequestEthernet.setDestinationMACAddress(BROADCAST_MAC);
      initRequestEthernet.setSourceMACAddress(iface.getMacAddress().toBytes()); // piazza@279
      initRequestEthernet.setEtherType(Ethernet.TYPE_IPv4);

      sendPacket(initRequestEthernet, iface);
      // System.out.println("*** -> Initial packet sent over iface: " + iface.getName()
      // + initRequestEthernet.toString().replace("\n", "\n\t"));
    }

    this.responseThread = new Thread(this);
    responseThread.start();
  }

  /**
   * Send unsolicited response every thirty seconds
   * 
   */
  public void run() {
    while (true) {
      // Run every 10 seconds
      try {
        Thread.sleep(UNSOLICITED_RESPONSE_INTERVAL);
      } catch (InterruptedException e) {
        break;
      }

      System.out.println("10 seconds have passed; Sending unsolicited RIP response");
      broadcastUnsolicitedRipReponse();
    }
  }

  private void broadcastUnsolicitedRipReponse() {
    // Send out unsolicited response
    UDP responseUdp = buildRipResponseDatagram();

    // Send out on each of this router's interfaces
    for (Iface iface : this.interfaces.values()) {
      Ethernet responseEthernet = buildRipResponseFrame(responseUdp, iface);

      sendPacket(responseEthernet, iface);
      // System.out.println("*** -> RIP response sent over iface: " + iface.getName()
      // + responseEthernet.toString().replace("\n", "\n\t"));
    }
  }

  private UDP buildRipResponseDatagram() {
    RIPv2 response = new RIPv2();

    List<RouteEntry> entries = this.routeTable.getEntries();
    synchronized (entries) {
      for (RouteEntry entry : entries) {
        int address = entry.getDestinationAddress();
        int subnetMask = entry.getMaskAddress();
        int metric = entry.getCost();

        RIPv2Entry newEntry = new RIPv2Entry(address, subnetMask, metric);
        response.addEntry(newEntry);
      }
    }
    // System.out.println(response);

    response.setCommand(RIPv2.COMMAND_RESPONSE);
    UDP responseUdp = new UDP();
    responseUdp.setPayload(response);
    responseUdp.setSourcePort(UDP.RIP_PORT);
    responseUdp.setDestinationPort(UDP.RIP_PORT);

    return responseUdp;
  }

  private static Ethernet buildRipResponseFrame(UDP responseUdp, Iface iface) {
    IPv4 responseIPv4 = new IPv4();
    responseIPv4.setPayload(responseUdp);
    responseIPv4.setDestinationAddress(MULTICAST_RIP);
    responseIPv4.setSourceAddress(iface.getIpAddress()); // piazza@279
    responseIPv4.setProtocol(IPv4.PROTOCOL_UDP);
    Ethernet responseEthernet = new Ethernet();
    responseEthernet.setPayload(responseIPv4);
    responseEthernet.setDestinationMACAddress(BROADCAST_MAC);
    responseEthernet.setSourceMACAddress(iface.getMacAddress().toBytes()); // piazza@279
    responseEthernet.setEtherType(Ethernet.TYPE_IPv4);

    return responseEthernet;
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

    if (isRipRouter) { // piazza@315 - only use RIP when configured for it
      if (etherPacket.getEtherType() == Ethernet.TYPE_IPv4) {
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        // Commented out after piazza@319
        // if (ipPacket.getDestinationAddress() != IPv4.toIPv4Address(MULTICAST_RIP)) {
        // this.handleIpPacket(etherPacket, inIface);
        // return;
        // }
        if (ipPacket.getProtocol() != IPv4.PROTOCOL_UDP) {
          this.handleIpPacket(etherPacket, inIface);
          return;
        }
        UDP udpPacket = (UDP) ipPacket.getPayload();
        if ((udpPacket.getDestinationPort() != UDP.RIP_PORT)
            || (udpPacket.getSourcePort() != UDP.RIP_PORT)) {
          this.handleIpPacket(etherPacket, inIface);
          return;
        }

        RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
        int nextHopIp = ipPacket.getSourceAddress();
        handleRip(ripPacket, nextHopIp, inIface);
      }
    } else { // otherwise, if not RIP router, do not handle RIP packets
      switch (etherPacket.getEtherType()) {
        case Ethernet.TYPE_IPv4:
          this.handleIpPacket(etherPacket, inIface);
          break;
        // Ignore all other packet types, for now
      }
    }

  }

  private void handleRip(RIPv2 ripPacket, int nextHopIp, Iface inIface) {
    // Request
    if (ripPacket.getCommand() == RIPv2.COMMAND_REQUEST) {
      System.out.println("Handle RIP request");
      // Send response only to requester piazza@322
      UDP responseUdp = buildRipResponseDatagram();
      Ethernet responseEthernet = buildRipResponseFrame(responseUdp, inIface);
      sendPacket(responseEthernet, inIface);
    }

    // Response
    if (ripPacket.getCommand() == RIPv2.COMMAND_RESPONSE) {
      boolean isRouteTableUpdated = false;

      System.out.println("Handle RIP response");
      List<RIPv2Entry> ripEntries = ripPacket.getEntries();
      for (RIPv2Entry entry : ripEntries) {
        if (mergeRoute(entry, nextHopIp, inIface) == true) {
          isRouteTableUpdated = true;
        }
      }

      // Perform a simplified triggered update response - piazza@356_f1
      if (isRouteTableUpdated) {
        System.out.println("Sending simplified triggered update RIP response");
        broadcastUnsolicitedRipReponse();
        System.out.println("Updated route table after handling RIP");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
      } else {
        System.out.println("No updates required after handling RIP");
      }
    }

  }

  /**
   * Bellman-ford distributed algorithm (basic distance vector)
   * 
   * @param ripEntry
   * @param nextHopIp
   * @param inIface
   * @return true if route table was updated, otherwise false
   */
  private boolean mergeRoute(RIPv2Entry ripEntry, int nextHopIp, Iface inIface) {
    int newDestIp = ripEntry.getAddress();
    int newSubnetMask = ripEntry.getSubnetMask();
    int newCost = ripEntry.getMetric() + 1;
    if (newCost >= 16) { // cost is infinite, so ignore piazza@327
      return false;
    }

    RouteEntry routeEntry;
    if ((routeEntry = routeTable.lookup(newDestIp)) != null) {
      if ((newCost < routeEntry.getCost())
          || ((newCost != routeEntry.getCost()) && (nextHopIp == routeEntry.getGatewayAddress()))) {
        // Update route entry with better route or new cost for current next hop
        routeTable.update(newDestIp, newSubnetMask, nextHopIp, inIface, newCost);
        System.out.println("\tUpdate rt entry: " + routeTable.lookup(newDestIp));
        return true;
      } else {
        if ((newCost == routeEntry.getCost()) && (nextHopIp == routeEntry.getGatewayAddress())) {
          // same route and cost received - refresh the TTL
          routeEntry.setTtl(RouteEntry.TTL_INIT_SEC);
        }

        return false;
      }
    } else {
      // add new route table entry
      routeTable.insert(newDestIp, nextHopIp, newSubnetMask, inIface, RouteEntry.TTL_INIT_SEC,
          newCost);
      System.out.println("\tInsert rt entry: " + routeTable.lookup(newDestIp));

      return true;
    }
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
