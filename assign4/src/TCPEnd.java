import java.io.IOException;
import java.net.InetAddress;

public class TCPEnd {
  public static void main(String[] args) throws IOException {
    int senderSourcePort = -1;
    int receiverPort = -1;
    InetAddress receiverIp = null;
    String filename = null;
    int mtu = -1;
    int sws = -1;

    if (args.length == 12) { // TCPEnd sender mode
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

      if (receiverIp == null || receiverPort == -1 || senderSourcePort == -1 || sws == -1
          || mtu == -1 || filename == null) {
        System.out.println(
            "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      }
      
      long startTime = System.nanoTime();

      Sender sender = new Sender(senderSourcePort, receiverIp, receiverPort, filename, mtu, sws);
      sender.openConnection();
      sender.sendData();
      sender.closeConnection();
      sender.socket.close();
      sender.printFinalStatsHeader();
      
      long endTime = System.nanoTime();
      long runTime = (endTime - startTime) / 1000000000;
      System.out.println("=====Other Stats=====");
      System.out.println("    Runtime (s): " + runTime);
      
    } else if (args.length == 8) { // TCPEnd receiver mode
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

      if (receiverPort == -1 || mtu == -1 || sws == -1 || filename == null) {
        System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
      }
      
      long startTime = System.nanoTime();
      
      Receiver receiver = new Receiver(receiverPort, filename, mtu, sws);
      receiver.openConnection();
      receiver.receiveDataAndClose();
      receiver.socket.close();
      receiver.printFinalStatsHeader();
      
      long endTime = System.nanoTime();
      long runTime = (endTime - startTime) / 1000000000;
      System.out.println("=====Other Stats=====");
      System.out.println("    Runtime (s): " + runTime);
    } else {
      System.out.println(
          "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
    }
  }
}
