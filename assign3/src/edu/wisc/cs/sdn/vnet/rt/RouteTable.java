package edu.wisc.cs.sdn.vnet.rt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.floodlightcontroller.packet.IPv4;
import edu.wisc.cs.sdn.vnet.Iface;

/**
 * Route table for a router.
 * 
 * @author Aaron Gember-Jacobson
 */
public class RouteTable implements Runnable {
  private Thread timeoutThread;
  // public static final int TIMEOUT = 30 * 1000;

  /** Entries in the route table */
  private List<RouteEntry> entries;

  /**
   * Initialize an empty route table.
   */
  public RouteTable() {
    this.entries = new LinkedList<RouteEntry>();
    timeoutThread = new Thread(this);
    timeoutThread.start();
  }
  
  public List<RouteEntry> getEntries() {
    return this.entries;
  }

  /**
   * Lookup the route entry that matches a given IP address.
   * 
   * @param ip IP address
   * @return the matching route entry, null if none exists
   */
  public RouteEntry lookup(int ip) {
    synchronized (this.entries) {
      /*****************************************************************/
      /* TODO: Find the route entry with the longest prefix match */

      RouteEntry bestMatch = null;
      for (RouteEntry entry : this.entries) {
        int maskedDst = ip & entry.getMaskAddress();
        int entrySubnet = entry.getDestinationAddress() & entry.getMaskAddress();
        if (maskedDst == entrySubnet) {
          if ((null == bestMatch) || (entry.getMaskAddress() > bestMatch.getMaskAddress())) {
            bestMatch = entry;
          }
        }
      }

      return bestMatch;

      /*****************************************************************/
    }
  }

  /**
   * Populate the route table from a file.
   * 
   * @param filename name of the file containing the static route table
   * @param router   the route table is associated with
   * @return true if route table was successfully loaded, otherwise false
   */
  public boolean load(String filename, Router router) {
    // Open the file
    BufferedReader reader;
    try {
      FileReader fileReader = new FileReader(filename);
      reader = new BufferedReader(fileReader);
    } catch (FileNotFoundException e) {
      System.err.println(e.toString());
      return false;
    }

    while (true) {
      // Read a route entry from the file
      String line = null;
      try {
        line = reader.readLine();
      } catch (IOException e) {
        System.err.println(e.toString());
        try {
          reader.close();
        } catch (IOException f) {
        } ;
        return false;
      }

      // Stop if we have reached the end of the file
      if (null == line) {
        break;
      }

      // Parse fields for route entry
      String ipPattern = "(\\d+\\.\\d+\\.\\d+\\.\\d+)";
      String ifacePattern = "([a-zA-Z0-9]+)";
      Pattern pattern = Pattern.compile(
          String.format("%s\\s+%s\\s+%s\\s+%s", ipPattern, ipPattern, ipPattern, ifacePattern));
      Matcher matcher = pattern.matcher(line);
      if (!matcher.matches() || matcher.groupCount() != 4) {
        System.err.println("Invalid entry in routing table file");
        try {
          reader.close();
        } catch (IOException f) {
        } ;
        return false;
      }

      int dstIp = IPv4.toIPv4Address(matcher.group(1));
      if (0 == dstIp) {
        System.err.println(
            "Error loading route table, cannot convert " + matcher.group(1) + " to valid IP");
        try {
          reader.close();
        } catch (IOException f) {
        } ;
        return false;
      }

      int gwIp = IPv4.toIPv4Address(matcher.group(2));

      int maskIp = IPv4.toIPv4Address(matcher.group(3));
      if (0 == maskIp) {
        System.err.println(
            "Error loading route table, cannot convert " + matcher.group(3) + " to valid IP");
        try {
          reader.close();
        } catch (IOException f) {
        } ;
        return false;
      }

      String ifaceName = matcher.group(4).trim();
      Iface iface = router.getInterface(ifaceName);
      if (null == iface) {
        System.err.println("Error loading route table, invalid interface " + matcher.group(4));
        try {
          reader.close();
        } catch (IOException f) {
        } ;
        return false;
      }

      // Add an entry to the route table
      this.insert(dstIp, gwIp, maskIp, iface);
    }

    // Close the file
    try {
      reader.close();
    } catch (IOException f) {
    } ;
    return true;
  }

  /**
   * Add an entry to the route table for RIPv2
   * 
   * @param dstIp  destination IP
   * @param gwIp   gateway IP
   * @param maskIp subnet mask
   * @param iface  router interface out which to send packets to reach the destination or gateway
   * @param time   to live
   */
  public void insert(int dstIp, int gwIp, int maskIp, Iface iface, int ttl, int cost) {
    RouteEntry entry = new RouteEntry(dstIp, gwIp, maskIp, iface, ttl, cost);
    synchronized (this.entries) {
      this.entries.add(entry);
    }
  }

  /**
   * Add an entry to the route table.
   * 
   * @param dstIp  destination IP
   * @param gwIp   gateway IP
   * @param maskIp subnet mask
   * @param iface  router interface out which to send packets to reach the destination or gateway
   */
  public void insert(int dstIp, int gwIp, int maskIp, Iface iface, int cost) {
    RouteEntry entry = new RouteEntry(dstIp, gwIp, maskIp, iface, cost);
    synchronized (this.entries) {
      this.entries.add(entry);
    }
  }
  
  /**
   * Add an entry to the route table.
   * 
   * @param dstIp  destination IP
   * @param gwIp   gateway IP
   * @param maskIp subnet mask
   * @param iface  router interface out which to send packets to reach the destination or gateway
   */
  public void insert(int dstIp, int gwIp, int maskIp, Iface iface) {
    RouteEntry entry = new RouteEntry(dstIp, gwIp, maskIp, iface);
    synchronized (this.entries) {
      this.entries.add(entry);
    }
  }

  /**
   * Remove an entry from the route table.
   * 
   * @param dstIP  destination IP of the entry to remove
   * @param maskIp subnet mask of the entry to remove
   * @return true if a matching entry was found and removed, otherwise false
   */
  public boolean remove(int dstIp, int maskIp) {
    synchronized (this.entries) {
      RouteEntry entry = this.find(dstIp, maskIp);
      if (null == entry) {
        return false;
      }
      this.entries.remove(entry);
    }
    return true;
  }
  
  /***
   * Reset TTL to thirty seconds. piazza@316
   * 
   * @param dstIp
   * @param maskIp
   * @param ttl
   * @return
   */
  public boolean resetTtl(int dstIp, int maskIp) {
    synchronized (this.entries) {
      RouteEntry entry = this.find(dstIp, maskIp);
      if (null == entry) {
        return false;
      }
      entry.setTtl(RouteEntry.TTL_INIT_SEC);
    }
    return true;
  }
  
  /**
   * Update an entry in the route table.
   * 
   * @param dstIP          destination IP of the entry to update
   * @param maskIp         subnet mask of the entry to update
   * @param gatewayAddress new gateway IP address for matching entry
   * @param iface          new router interface for matching entry
   * @param cost           
   * @return true if a matching entry was found and updated, otherwise false
   */
  public boolean update(int dstIp, int maskIp, int gwIp, Iface iface, int cost) {
    this.update(dstIp, maskIp, gwIp, iface);
    synchronized (this.entries) {
      RouteEntry entry = this.find(dstIp, maskIp);
      if (null == entry) {
        return false;
      }
      entry.setCost(cost);
    }
    return true;
  }

  /**
   * Update an entry in the route table.
   * 
   * @param dstIP          destination IP of the entry to update
   * @param maskIp         subnet mask of the entry to update
   * @param gatewayAddress new gateway IP address for matching entry
   * @param iface          new router interface for matching entry
   * @return true if a matching entry was found and updated, otherwise false
   */
  public boolean update(int dstIp, int maskIp, int gwIp, Iface iface) {
    synchronized (this.entries) {
      RouteEntry entry = this.find(dstIp, maskIp);
      if (null == entry) {
        return false;
      }
      entry.setGatewayAddress(gwIp);
      entry.setInterface(iface);
      entry.setTtl(RouteEntry.TTL_INIT_SEC);
    }
    return true;
  }

  /**
   * Find an entry in the route table.
   * 
   * @param dstIP  destination IP of the entry to find
   * @param maskIp subnet mask of the entry to find
   * @return a matching entry if one was found, otherwise null
   */
  private RouteEntry find(int dstIp, int maskIp) {
    synchronized (this.entries) {
      for (RouteEntry entry : this.entries) {
        if ((entry.getDestinationAddress() == dstIp) && (entry.getMaskAddress() == maskIp)) {
          return entry;
        }
      }
    }
    return null;
  }

  public String toString() {
    synchronized (this.entries) {
      if (0 == this.entries.size()) {
        return " WARNING: route table empty";
      }

      String result = "Destination\tGateway\t\tMask\t\tIface\tTtl\tCost\n";
      for (RouteEntry entry : entries) {
        result += entry.toString() + "\n";
      }
      return result;
    }
  }
  
  public List<Integer> getDestIpList() {
    List<Integer> destIpList = new LinkedList<Integer>();
    for (RouteEntry entry : this.entries) {
      destIpList.add(entry.getDestinationAddress());
    }
    
    return destIpList;
  }

  /**
   * Expire after thirty seconds
   */
  public void run() {
    while (true) {
      // Run every second
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        break;
      }

      // Timeout entries
      boolean hasRemovedEntry = false;
      synchronized (this.entries) {
        List<Integer> destIpList = this.getDestIpList();
        for (Integer destIp : destIpList) {
          RouteEntry entry = this.lookup(destIp); 
          if (entry.getTtl() == -1) {
            continue;
          } else if (entry.getTtl() == 0) {
            this.entries.remove(entry);
            System.out.println("\tRemove entry: " + entry);
            hasRemovedEntry = true;
          } else {
            entry.decrementTtl();
//            System.out.println("Decrement routeEntry:" + entry);
          }
        }
      }
      
      if (hasRemovedEntry) {
        System.out.println("Route table after removal/TTL expiration");
        System.out.println("-------------------------------------------------");
        System.out.print(this.toString());
        System.out.println("-------------------------------------------------");
      }
    }
  }
}
