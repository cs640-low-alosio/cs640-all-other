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
		
		// Drop if not IPv4
		if (etherPacket.getEtherType() != 0x0800) { // TODO: are these redundant?
		  System.out.println("Router.handlePacket() - not an IPv4 packet; dropping");
		  return;
		}
		
		if (!(etherPacket.getPayload() instanceof IPv4)) { // TODO: are these redundant?
		  System.out.println("Router.handlePacket() - not an IPv4 packet; dropping");
		  return;
        }
		
		
		// Otherwise, handle packet
		IPv4 ipacket = (IPv4) etherPacket.getPayload();
		int destIp = ipacket.getDestinationAddress();
		System.out.println("Router.handlePacket() - destIp: " + destIp);
		
		// Checksum
		short expChecksum = ipacket.getChecksum();
		System.out.println("Router.handlePacket() - expChecksum: " + expChecksum);
		// TODO: might be inefficient and doesn't use headerLength
		ipacket.setChecksum((short) 0);
		ipacket.serialize();
		short actChecksum = ipacket.getChecksum();
		System.out.println("Router.handlePacket() - ActChecksum: " + actChecksum);
		if (expChecksum != actChecksum) {
		  System.out.println("Router.handlePacket() - Checksum mismatch; dropping");
		  return;
		}
		
		// TTL
		byte ttl = ipacket.getTtl();
		ttl--;
		System.out.println("Router.handlePacket() - TTL: " + ttl);
		if (ttl == 0) {
		  System.out.println("Router.handlePacket() - TTL reached 0; dropping");
		  return;
		}
		ipacket.setTtl(ttl);
		ipacket.setChecksum((short) 0);
		ipacket.serialize(); // recalculate checksum with new TTL
		
		// Make sure packet is not destined for the same router
		Set<String> faceSet = this.interfaces.keySet();
		for (String faceName : faceSet) {
		  Iface face = this.interfaces.get(faceName);
		  if (destIp == face.getIpAddress()) {
		    System.out.println("Router.handlePacket() - packet source same as destination; dropping");
		    return;
		  }
        }
		
		// Setup for sending
		RouteEntry bestRouteEntry = routeTable.lookup(destIp);
		if (bestRouteEntry == null ) { // if no entry matches, drop
		  System.out.println("Router.handlePacket() - no matching entry in routing table for given destination IP; dropping");
		  return;
		}
		
		int arpLookupIp = bestRouteEntry.getGatewayAddress();
		if (arpLookupIp == 0) {
		  System.out.println("Router.handlePacket() - destination IP in current network, sending to destination IP address");
		  arpLookupIp = destIp;
		}
		System.out.println("Router.handlePacket() - arpLookupIp: " + arpLookupIp);
		
		ArpEntry outArpEntry = arpCache.lookup(arpLookupIp);
		if (outArpEntry == null) { // TODO: do we need this?
		  System.out.println("Router.handlePacket() - no arp entry found");
		  return;
		}
		
		MACAddress newDestMacAddr = outArpEntry.getMac();
		System.out.println("Router.handlePacket() - newDestMacAddr: " + newDestMacAddr);
		
		Iface outIface = bestRouteEntry.getInterface();
		System.out.println("Router.handlePacket() - outIface name: " + outIface.getName());
		MACAddress newSourceMacAddr = outIface.getMacAddress();
		System.out.println("Router.handlePacket() - newSourceMacAddr: " + newSourceMacAddr);
		
		etherPacket.setDestinationMACAddress(newDestMacAddr.toString());
		etherPacket.setSourceMACAddress(newSourceMacAddr.toString());
		
		sendPacket(etherPacket, outIface);

	}
	
//	public static short calculateChecksum(IPv4 ipacket, short headerLength) {
//	  
//      bb.put((byte) (((ipacket.version & 0xf) << 4) | (ipacket.headerLength & 0xf)));
//      bb.put(ipacket.diffServ);
//      bb.putShort(ipacket.totalLength);
//      bb.putShort(ipacket.identification);
//      bb.putShort((short) (((ipacket.flags & 0x7) << 13) | (ipacket.fragmentOffset & 0x1fff)));
//      bb.put(ipacket.ttl);
//      bb.put(ipacket.protocol);
//      bb.putShort(ipacket.checksum);
//      bb.putInt(ipacket.sourceAddress);
//      bb.putInt(ipacket.destinationAddress);
//      if (ipacket.options != null)
//          bb.put(ipacket.options);
////      if (payloadData != null)
////          bb.put(payloadData);
//
//      // compute checksum if needed
//      if (this.checksum == 0) {
//          bb.rewind();
//          int accumulation = 0;
//          for (int i = 0; i < this.headerLength * 2; ++i) {
//              accumulation += 0xffff & bb.getShort();
//          }
//          accumulation = ((accumulation >> 16) & 0xffff)
//                  + (accumulation & 0xffff);
//          this.checksum = (short) (~accumulation & 0xffff);
//          bb.putShort(10, this.checksum);
//      }
//      return data;
//    return 0;
//      
//    }
}
