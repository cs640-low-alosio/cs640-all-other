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
        // if (!switchTable.isEmpty()) {
        // System.out.println("*** -> Switch.run()");
        // }
        synchronized (switchTable) {
          List<MACAddress> macAddrSet = new ArrayList<>(switchTable.keySet());
          // for (MACAddress macAddress : macAddrSet) {
          for (Iterator<MACAddress> iterator = macAddrSet.iterator(); iterator.hasNext();) {
            MACAddress macAddress = iterator.next();
            SwitchEntry switchEntry = switchTable.get(macAddress);
            if (switchEntry.getTtl() != 0) {
              // System.out.println("\tdecrement ttl: " + switchEntry.getMacAddr() + ", iface: "
              // + switchEntry.getIface() + ", ttl: " + switchEntry.getTtl());
              switchEntry.decrementTtl();
            } else {
              // System.out.println("\texpire entry: " + switchEntry.getMacAddr() + ", iface: "
              // + switchEntry.getIface() + ", ttl: " + switchEntry.getTtl());
              switchTable.remove(macAddress);
            }
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
    synchronized (switchTable) {
      switchTable.put(sourceMac, new SwitchEntry(sourceMac, inIface));
    }

    SwitchEntry outEntry = null;
    if ((outEntry = switchTable.get(destMac)) != null) { // Switch table has matching entry
      // System.out.println("\t- matching switch table entry found!");
      // System.out.println("\t- dest mac: " + destMac + ", out iface: " + outEntry.getIface());
      if (outEntry.getIface() == inIface) { // Drop frame if inIface same as outIface
        return;
      } else {
        // System.out.println("\t- sending to ifacename: " + outEntry.getIface());
        sendPacket(etherPacket, outEntry.getIface());
      }
    } else { // Flood all
      Set<String> faceNameSet = interfaces.keySet();
      for (String faceName : faceNameSet) {
        if (faceName == inIface.getName()) { // except incoming interface
          continue;
        }
        // System.out.println("\t- flood trying: " + faceName);
        if (sendPacket(etherPacket, interfaces.get(faceName))) {
          // System.out.println("\t- sending to ifacename: " + faceName);
        }
      }
    }
  }
}
