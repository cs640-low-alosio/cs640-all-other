package edu.wisc.cs.sdn.vnet.rt;

import net.floodlightcontroller.packet.IPv4;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * An entry in a route table.
 * 
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class RouteEntry {
  public static final int TTL_INIT_SECONDS = 30;
  
  /** Destination IP address */
  private int destinationAddress;

  /** Gateway IP address */
  private int gatewayAddress;

  /** Subnet mask */
  private int maskAddress;

  /**
   * Router interface out which packets should be sent to reach the destination or gateway
   */
  private Iface iface;
  
  /**
   * Time before entries expire
   */
  private int ttl;
  
  /**
   * Metric for RIPv2
   */
  private int cost;

  /**
   * Create a new route table entry for RIPv2
   * 
   * @param destinationAddress destination IP address
   * @param gatewayAddress     gateway IP address
   * @param maskAddress        subnet mask
   * @param iface              the router interface out which packets should be sent to reach the
   *                           destination or gateway
   * @param ttl
   */
  public RouteEntry(int destinationAddress, int gatewayAddress, int maskAddress, Iface iface,
      int ttl, int cost) {
    this.destinationAddress = destinationAddress;
    this.gatewayAddress = gatewayAddress;
    this.maskAddress = maskAddress;
    this.iface = iface;
    this.ttl = ttl;
    this.cost = cost;
  }

  /**
   * Create a new route table entry.
   * 
   * @param destinationAddress destination IP address
   * @param gatewayAddress     gateway IP address
   * @param maskAddress        subnet mask
   * @param iface              the router interface out which packets should be sent to reach the
   *                           destination or gateway
   */
  public RouteEntry(int destinationAddress, int gatewayAddress, int maskAddress, Iface iface, int cost) {
    this.destinationAddress = destinationAddress;
    this.gatewayAddress = gatewayAddress;
    this.maskAddress = maskAddress;
    this.iface = iface;
    this.ttl = -1;
    this.cost = cost;
  }
  
  /**
   * Create a new route table entry.
   * 
   * @param destinationAddress destination IP address
   * @param gatewayAddress     gateway IP address
   * @param maskAddress        subnet mask
   * @param iface              the router interface out which packets should be sent to reach the
   *                           destination or gateway
   */
  public RouteEntry(int destinationAddress, int gatewayAddress, int maskAddress, Iface iface) {
    this.destinationAddress = destinationAddress;
    this.gatewayAddress = gatewayAddress;
    this.maskAddress = maskAddress;
    this.iface = iface;
    this.ttl = -1;
  }

  /**
   * @return destination IP address
   */
  public int getDestinationAddress() {
    return this.destinationAddress;
  }

  /**
   * @return gateway IP address
   */
  public int getGatewayAddress() {
    return this.gatewayAddress;
  }

  public void setGatewayAddress(int gatewayAddress) {
    this.gatewayAddress = gatewayAddress;
  }

  /**
   * @return subnet mask
   */
  public int getMaskAddress() {
    return this.maskAddress;
  }

  /**
   * @return the router interface out which packets should be sent to reach the destination or
   *         gateway
   */
  public Iface getInterface() {
    return this.iface;
  }

  public void setInterface(Iface iface) {
    this.iface = iface;
  }

  public String toString() {
    return String.format("%s \t%s \t%s \t%s \t%s", IPv4.fromIPv4Address(this.destinationAddress),
        IPv4.fromIPv4Address(this.gatewayAddress), IPv4.fromIPv4Address(this.maskAddress),
        this.iface.getName(), this.ttl);
  }

  public int getTtl() {
    return this.ttl;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }

  public void decrementTtl() {
    this.ttl--;
  }
  
  public void setCost(int cost) {
    this.cost = cost;
  }
  
  public int getCost() {
    return this.cost;
  }
}
