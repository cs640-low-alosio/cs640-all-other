package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import java.util.HashMap;
import java.util.Set;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {
  private HashMap<MACAddress, SwitchEntry> switchTable;

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Switch(String host, DumpFile logfile) {
    super(host, logfile);
    this.switchTable = new HashMap<MACAddress, SwitchEntry>();
  }

  /**
   * Handle an Ethernet packet received on a specific interface.
   * 
   * @param etherPacket the Ethernet packet that was received
   * @param inIface     the interface on which the packet was received
   */
  public void handlePacket(Ethernet etherPacket, Iface inIface) {
    System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

    MACAddress sourceMac = etherPacket.getSourceMAC();
    MACAddress destMac = etherPacket.getDestinationMAC();
    // SwitchEntry inSwitchEntry = new SwitchEntry(sourceMac, inIface);
    // Reset switch table entry for source MACAddr and interface
    switchTable.put(sourceMac, new SwitchEntry(sourceMac, inIface)); 
    
    // should we handle three cases separately?
    // interface is not in switch table
    // interface is in table and not expired,
    // interface is in table and expired (basically same as first case)
    
    SwitchEntry outEntry = null;
    if ((outEntry = switchTable.get(destMac)) != null ) {
      System.out.println("DEBUG: matching switch table entry found!"); // debug
      System.out.println("DEBUG: dest mac: " + destMac + ", switch table entry: "+ outEntry.getIface()); // debug
      if (outEntry.getIface() == inIface) { // drop frame if inIface same as outIface
        // use VNSComm.java etherAddrsMatchInterface() instead?
        return;
      } else {
        System.out.println("DEBUG: sending to ifacename: " + outEntry.getIface()); // debug
        sendPacket(etherPacket, outEntry.getIface());
      }
    } else { // flood all
      Set<String> faceNameSet = interfaces.keySet();
      for (String faceName : faceNameSet) {
        if (faceName == inIface.getName()) { // except incoming interface
          continue;
        }
        System.out.println("DEBUG: flood trying: " + faceName); // debug
        if (sendPacket(etherPacket, interfaces.get(faceName))) {
          System.out.println("DEBUG: sending to ifacename: " + faceName); // debug
          break; // break when sendPacket is true?
        }
      }
    }

    /********************************************************************/
    /* TODO: Handle packets */

    /********************************************************************/
  }
}
