package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Iface;

public class SwitchEntry {
  private Iface iface;
  private MACAddress macAddr;
//  private long startTime;
  private int ttl;
  private static int DEFAULT_TTL=15;

  public SwitchEntry(MACAddress macAddr, Iface iface) {
    this.macAddr = macAddr;
    this.iface = iface;
//    this.startTime = System.nanoTime() / 1000000000;
    this.ttl = DEFAULT_TTL;
  }

  public MACAddress getMacAddr() {
    return this.macAddr;
  }

  public Iface getIface() {
    return this.iface;
  }

//  public long getStartTime() {
//    return this.startTime;
//  }
  
  public int getTtl() {
    return this.ttl;
  }

  public void decrementTtl() {
    this.ttl--;
  }
}
