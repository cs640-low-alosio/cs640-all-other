import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class TCPEnd {
  public static void main(String[] args) throws IOException {
    int senderSourcePort = -1;
    int receiverPort = -1;
    InetAddress receiverIp = null;
    String filename = null;
    int mtu = -1;
    int sws = -1;

    // TODO: move to Sender class
    int bsNumSender = 0;
    long senderTimeout = 5000000000L; // initial timeout in nanoseconds
    int bsNumReceiver = 0; // TODO: move to Receiver class
    long rcvrTimeout = 5000000000L; // initial timeout in nanoseconds

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

      if (receiverIp == null || receiverPort == -1 || senderSourcePort == -1 || sws == -1 || mtu == -1) {
        System.out.println(
            "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      }

      DatagramSocket senderSocket = new DatagramSocket(senderSourcePort);
      // byte[] bytes = new byte[5];
      // DatagramPacket packet = new DatagramPacket(bytes, 5, receiverIp, receiverPort);
      
      // TODO: Retransmission of handshake (hopefully same implementation as data transfer) @395
      // Send First Syn Packet
      GoBackNPacket handshakeSyn = new GoBackNPacket();
      handshakeSyn.setSyn(true);
      handshakeSyn.setByteSequenceNum(bsNumSender);
      handshakeSyn.setTimestamp(System.nanoTime());
      handshakeSyn.setLength(0);
      byte[] handshakeSynData = handshakeSyn.serialize();

      DatagramPacket handshakeSynPacket =
          new DatagramPacket(handshakeSynData, handshakeSynData.length, receiverIp, receiverPort);
      System.out.println("Sender first syn chk: " + handshakeSyn.getChecksum());
      bsNumSender++;
      senderSocket.send(handshakeSynPacket);
      
      // Receive 2nd Syn+Ack Packet
      byte[] hsSynAckBytes = new byte[mtu];
      DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, mtu);
      senderSocket.receive(hsSynAckPacket);
      hsSynAckBytes = hsSynAckPacket.getData();
      GoBackNPacket hsSynAck = new GoBackNPacket();
      hsSynAck.deserialize(hsSynAckBytes);
      System.out.println("Sndr syn+ack chk: " + hsSynAck.getChecksum());
      
      // Verify checksum first syn packet
      short origChk = hsSynAck.getChecksum();
      hsSynAck.resetChecksum();
      hsSynAck.serialize();
      short calcChk = hsSynAck.getChecksum();
      if (origChk != calcChk) {
        System.out.println("Sender - Syn+Ack chk does not match!");
      }
      
      // Send 3rd Ack Packet
      GoBackNPacket hsAck = new GoBackNPacket();
      hsAck.setSyn(true);
      hsAck.setByteSequenceNum(bsNumSender);
      hsAck.setTimestamp(System.nanoTime());
      hsAck.setLength(0);
      byte[] hsAckBytes = hsAck.serialize();

      DatagramPacket hsAckUdp =
          new DatagramPacket(hsAckBytes, hsAckBytes.length, receiverIp, receiverPort);
      System.out.println("Sndr - ack chk: " + hsAck.getChecksum());
      bsNumSender++;
      senderSocket.send(hsAckUdp);

      // syn1.setAck(true);
      // syn1.setByteSequenceNum(1);
      // byte[] data = syn1.serialize();
      //
      // for (int i = 0; i < data.length; i++) {
      // byte[] packetBytes = new byte[sws];
      //
      // for (int j = 0; j < sws; j++) {
      // packetBytes[j] = data[i];
      // }
      //
      // DatagramPacket packet = new DatagramPacket(packetBytes, sws, receiverIp, receiverPort);
      // senderSocket.send(packet);
      // }
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

      if (receiverPort == -1 || mtu == -1 || sws == -1 || filename == null) {
        System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
      }
      
      DatagramSocket receiverSocket = new DatagramSocket(receiverPort);
      
      // Receive First Syn Packet
      byte[] bytes = new byte[mtu];
      DatagramPacket handshakeSynPacket = new DatagramPacket(bytes, mtu);
      receiverSocket.receive(handshakeSynPacket);
      byte[] handshakeSynBytes = handshakeSynPacket.getData();
      GoBackNPacket handshakeSyn = new GoBackNPacket();
      handshakeSyn.deserialize(handshakeSynBytes);
      System.out.println("Rcvr first syn chk: " + handshakeSyn.getChecksum());
      
      // Verify checksum first syn packet
      short origChk = handshakeSyn.getChecksum();
      handshakeSyn.resetChecksum();
      handshakeSyn.serialize();
      short calcChk = handshakeSyn.getChecksum();
      if (origChk != calcChk) {
        System.out.println("Rcvr - first syn chk does not match!");
      }
      
      // Send 2nd Syn+Ack Packet
      GoBackNPacket hsSynAck = new GoBackNPacket(bsNumReceiver, 0, true, false, true, new byte[0], 0);
      byte[] hsSynAckBytes = hsSynAck.serialize();
      DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, hsSynAckBytes.length, handshakeSynPacket.getAddress(), handshakeSynPacket.getPort());
      System.out.println("Rcvr - send syn+ack chk: " + handshakeSyn.getChecksum());
      bsNumReceiver++;
      receiverSocket.send(hsSynAckPacket);
      
      // Receive Ack Packet
      byte[] hsAckBytes = new byte[mtu];
      DatagramPacket hsAckUdp = new DatagramPacket(hsAckBytes, mtu);
      receiverSocket.receive(hsAckUdp);
      hsAckBytes = hsAckUdp.getData();
      GoBackNPacket hsAck = new GoBackNPacket();
      hsAck.deserialize(hsAckBytes);
      System.out.println("Rcvr - ack chk: " + hsAck.getChecksum());
      
      // Verify checksum first syn packet
      origChk = hsAck.getChecksum();
      hsAck.resetChecksum();
      hsAck.serialize();
      calcChk = hsAck.getChecksum();
      if (origChk != calcChk) {
        System.out.println("Rcvr - ack chk does not match!");
      }
      
      receiverSocket.close();
    } else {
      System.out.println(
          "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
    }
  }
}
