package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

public class SwitchEntry {
  private Iface iface;
  private MACAddress macAddr;
  private int ttl;
  private static int DEFAULT_TTL=15;
  
  public SwitchEntry(MACAddress macAddr, Iface iface) {
    this.macAddr = macAddr;
    this.iface = iface;
    this.ttl = DEFAULT_TTL;
  }
  
  public getMacAddr() {
    return this.macAddr;
  }
  
  public getIface() {
    return this.iface;
  }
  
  public getTtl() {
    return this.ttl;
  }
  
  public setTtl(int ttl) {
    this.ttl = ttl;
  }
}