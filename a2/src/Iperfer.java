import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import net.floodlightcontroller.packet.IPv4;

public class Iperfer {

  static final int BUF_SIZE = 1000;
  static final int BITS_PER_BYTE = 8;
  static DecimalFormat threePlaces = new DecimalFormat("0.000");

  public static void main(String[] args) {
    // Invalid arguments
    if (args[0].equals("-c")) {
      if (args.length != 7) {
        System.out.println("Error: missing or additional arguments");
        return;
      }
      if (Integer.parseInt(args[4]) < 1024 || Integer.parseInt(args[4]) > 65535) {
        System.out.println("Error: port number must be in the range 1024 to 65535");
        return;
      }
    }
    if (args[0].equals("-s")) {
      if (args.length != 3) {
        System.out.println("Error: missing or additional arguments");
        return;
      }
      if (Integer.parseInt(args[2]) < 1024 || Integer.parseInt(args[2]) > 65535) {
        System.out.println("Error: port number must be in the range 1024 to 65535");
        return;
      }
    }

    if (args[0].equals("-s")) { // Server Mode
      int port = Integer.parseInt(args[2]);
      
      Socket clientSocket = null;
      try (ServerSocket serverSocket = new ServerSocket(port)) {
        clientSocket = serverSocket.accept();
        DataInputStream in = new DataInputStream(clientSocket.getInputStream());

        long startTime = System.nanoTime();
        long charReadCount = 0;
        long totalChar = 0;

        byte[] readBuffer = new byte[BUF_SIZE];
        // while (in.read() != -1) {
        while ((charReadCount = in.read(readBuffer, 0, BUF_SIZE)) != -1) {
          // System.out.println("charReadCount: " + charReadCount); // debug
          totalChar += charReadCount;
        }
        
        // System.out.println("totalChar: " + totalChar);
        long totalKByte = totalChar / BUF_SIZE;
        // System.out.println("totalKB: " + totalKByte);
        long totalMbit = totalKByte * BITS_PER_BYTE / 1000;
        long serverDuration = (System.nanoTime() - startTime) / 1000000000;
        double rate = (double) totalMbit / serverDuration;
        
        // debug
        // System.out.println("server totalKByte read: " + totalKByte + ", starttime: " + startTime + ", rate: " + threePlaces.format(rate) + ", System.nanoTime: " + System.nanoTime() + ", denom: " + ((System.nanoTime() - startTime) / 1000000));
        // actual output
        System.out
            .println("received=" + totalKByte + " KB rate=" + threePlaces.format(rate) + " Mbps");
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        try {
          clientSocket.close(); 
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } else { // Client Mode
      int port = Integer.parseInt(args[4]);
      String serverIp = args[2];
      int testType = Integer.parseInt(args[6]);

      try {
        Socket clientSocket = new Socket(serverIp, port);
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
        
        if (testType == 1) {
          IPv4 badpacket = new IPv4();
          badpacket.setDestinationAddress(serverIp);
          badpacket.setChecksum((short) 123);
          badpacket.setTtl((byte) 0b1);
          out.write(badpacket.serialize());
        }
        
        clientSocket.close();
      } catch (UnknownHostException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
