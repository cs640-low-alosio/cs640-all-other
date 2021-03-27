import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TCPEnd {
  public static void main(String[] args) throws IOException {
    int senderSourcePort = -1;
    int receiverPort = -1;
    InetAddress receiverIp = null;
    String filename;
    int mtu;
    int sws = -1;


    if (args.length == 12) {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("-p")) {
          senderSourcePort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-s")) {
          receiverIp = InetAddress.getByName(args[++i]);
        } else if (arg.equals("-a")) {
          receiverPort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-f")) {
          filename = args[++i];
        } else if (arg.equals("-m")) {
          mtu = Integer.parseInt(args[++i]);
        } else if (arg.equals("-c")) {
          sws = Integer.parseInt(args[++i]);
        }
      }
      
      if (receiverIp == null || receiverPort == -1 || senderSourcePort == -1 || sws == -1) {
        System.out.println(
            "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      }
      
      DatagramSocket senderSocket = new DatagramSocket(senderSourcePort);
//      byte[] bytes = new byte[5];
//      DatagramPacket packet = new DatagramPacket(bytes, 5, receiverIp, receiverPort);
      GoBackNPacket syn1 = new GoBackNPacket();
      syn1.setAck(true);
      syn1.setByteSequenceNum(1);
      byte[] data = syn1.serialize();
      
      for (int i = 0; i < data.length; i++) {
        byte[] packetBytes = new byte[sws];
        
        for (int j = 0; j < sws; j++) {
          packetBytes[j] = data[i];
        }
        
        DatagramPacket packet = new DatagramPacket(packetBytes, sws, receiverIp, receiverPort);
        senderSocket.send(packet);
      }
      senderSocket.close();
      
    } else if (args.length == 8) {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        if (arg.equals("-p")) {
          receiverPort = Integer.parseInt(args[++i]);
        } else if (arg.equals("-f")) {
          filename = args[++i];
        } else if (arg.equals("-m")) {
          mtu = Integer.parseInt(args[++i]);
        } else if (arg.equals("-c")) {
          sws = Integer.parseInt(args[++i]);
        }
      }
      
      if (receiverPort == -1) {
        System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
      }
      
      DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
      byte[] bytes = new byte[5];
      DatagramPacket packet = new DatagramPacket(bytes, 5);
      receiverSocket.receive(packet);
      
      System.out.println(bytes);
      
      receiverSocket.close();
      
    } else {
      System.out.println(
          "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
    }
  }
}
