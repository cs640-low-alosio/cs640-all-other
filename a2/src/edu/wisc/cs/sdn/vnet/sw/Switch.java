package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {
  private HashMap<MACAddress, SwitchEntry> switchTable;
  static int counter = 0;

  /**
   * Creates a router for a specific host.
   * 
   * @param host hostname for the router
   */
  public Switch(String host, DumpFile logfile) {
    super(host, logfile);
    this.switchTable = new HashMap<MACAddress, SwitchEntry>();
    TimerTask timerTask = new TimerTask() {

      @Override
      public void run() {
        System.out.println("DEBUG: " + counter + " seconds");
        counter++;

        List<MACAddress> macAddrSet = new ArrayList<>(switchTable.keySet());
//        for (MACAddress macAddress : macAddrSet) {
        for (Iterator<MACAddress> iterator = macAddrSet.iterator(); iterator.hasNext();) {
          MACAddress macAddress = iterator.next();
          SwitchEntry switchEntry = switchTable.get(macAddress);
          if (switchEntry.getTtl() != 0) {
            System.out.println("DEBUG: decrementing iface: " + switchEntry.getIface()
                + ", macAddr: " + switchEntry.getMacAddr() + ", ttl: " + switchEntry.getTtl());
            switchEntry.decrementTtl();
          } else {
            System.out.println("DEBUG: removing iface: " + switchEntry.getIface() + ", macAddr: "
                + switchEntry.getMacAddr() + ", ttl: " + switchEntry.getTtl());
            switchTable.remove(macAddress);
          }
        }
      }
    };

    Timer timer = new Timer("MyTimer");// create a new Timer

    timer.scheduleAtFixedRate(timerTask, 0, 1000);
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
    if ((outEntry = switchTable.get(destMac)) != null) {
      System.out.println("DEBUG: matching switch table entry found!"); // debug
      System.out
          .println("DEBUG: dest mac: " + destMac + ", switch table entry: " + outEntry.getIface()); // debug
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
          // break; // break when sendPacket is true?
        }
      }
    }

    /********************************************************************/
    /* TODO: Handle packets */
    // TODO: NOT NECESSARILY THE INCOMING INTERFACE
    // TODO: better learning piazza @155
    // TODO: expiration @154

    /********************************************************************/
  }
}
