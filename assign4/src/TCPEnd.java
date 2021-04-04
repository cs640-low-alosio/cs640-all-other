import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Scanner;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;

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

      if (receiverIp == null || receiverPort == -1 || senderSourcePort == -1 || sws == -1
          || mtu == -1 || filename == null) {
        System.out.println(
            "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      }

      DatagramSocket senderSocket = new DatagramSocket(senderSourcePort);
      // byte[] bytes = new byte[5];
      // DatagramPacket packet = new DatagramPacket(bytes, 5, receiverIp, receiverPort);

      // TODO: Retransmission of handshake (hopefully same implementation as data transfer) @395
      // TODO: Check flags
      // TODO: does SYN flag occupy one byte in byte sequence number? piazza@###
      // Send First Syn Packet
      GBNSegment handshakeSyn = GBNSegment.createHandshakeSegment(bsNumReceiver, HandshakeType.SYN);
      byte[] handshakeSynData = handshakeSyn.serialize();
      DatagramPacket handshakeSynPacket =
          new DatagramPacket(handshakeSynData, handshakeSynData.length, receiverIp, receiverPort);
      System.out.println("Handshake: Sender first syn chk: " + handshakeSyn.getChecksum());
      bsNumSender++;
      senderSocket.send(handshakeSynPacket);

      // Receive 2nd Syn+Ack Packet
      byte[] hsSynAckBytes = new byte[mtu];
      DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, mtu);
      senderSocket.receive(hsSynAckPacket);
      hsSynAckBytes = hsSynAckPacket.getData();
      GBNSegment hsSynAck = new GBNSegment();
      hsSynAck.deserialize(hsSynAckBytes);
      System.out.println("Handshake: Sndr syn+ack chk: " + hsSynAck.getChecksum());

      // Verify checksum Syn+Ack packet
      short origChk = hsSynAck.getChecksum();
      hsSynAck.resetChecksum();
      hsSynAck.serialize();
      short calcChk = hsSynAck.getChecksum();
      if (origChk != calcChk) {
        System.out.println("Handshake: Sender - Syn+Ack chk does not match!");
      }
      if (!(hsSynAck.isSyn && hsSynAck.isAck)) {
        System.out.println("Handshake: Sender - Does not have syn+ack flag");
      }

      // Send 3rd Ack Packet
      GBNSegment hsAck = GBNSegment.createHandshakeSegment(bsNumSender, HandshakeType.ACK);
      byte[] hsAckBytes = hsAck.serialize();
      DatagramPacket hsAckUdp =
          new DatagramPacket(hsAckBytes, hsAckBytes.length, receiverIp, receiverPort);
      System.out.println("Handshake: Sndr - ack chk: " + hsAck.getChecksum());
      bsNumSender++;
      senderSocket.send(hsAckUdp);

      // Data Transfer
      try (InputStream in = new FileInputStream(filename)) {
        // inputStream = new BufferedReader(new FileReader(filename), mtu);
        DataInputStream inputStream = new DataInputStream(in);

        ArrayList<byte[]> sendBuffer = new ArrayList<>();

        byte[] onePayloadData = new byte[mtu];

        // Initial filling up send buffer
        int lastByteSent = 0;
        int lastByteAcked = 0; // TODO: or could be 1 after SYN
        int lastByteWritten = 0;
        int effectiveWindow = 0;
        int advertisedWindow = sws;
        int maxSenderBuffer = sws * 10; // TODO: can choose whatever we want?
        int byteReadCount;
//        while (((byteReadCount = inputStream.read(onePayloadData, lastByteWritten, mtu)) != -1)
//            && (lastByteWritten - lastByteAcked <= maxSenderBuffer)) {
//          sendBuffer.add(onePayloadData);
//          lastByteWritten += byteReadCount;
//        }
        do {
          // Write to Send Buffer "Application" Code
          while (((byteReadCount = inputStream.read(onePayloadData, lastByteWritten, mtu)) != -1)
              && (lastByteWritten - lastByteAcked <= maxSenderBuffer)) {
            sendBuffer.add(onePayloadData);
            lastByteWritten += byteReadCount;
          }
          
          effectiveWindow = advertisedWindow - (lastByteSent - lastByteAcked);
          
          // Nagle's Algorithm

        } while (sendBuffer.size() > 0);
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

      if (receiverPort == -1 || mtu == -1 || sws == -1 || filename == null) {
        System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
      }

      DatagramSocket receiverSocket = new DatagramSocket(receiverPort);

      // Receive First Syn Packet
      byte[] bytes = new byte[mtu];
      DatagramPacket handshakeSynPacket = new DatagramPacket(bytes, mtu);
      receiverSocket.receive(handshakeSynPacket);
      byte[] handshakeSynBytes = handshakeSynPacket.getData();
      GBNSegment handshakeSyn = new GBNSegment();
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
      if (!handshakeSyn.isSyn || handshakeSyn.isAck || handshakeSyn.isFin) {
        System.out.println("Handshake: Rcvr - first segment doesn't have SYN flag!");
      }

      // Send 2nd Syn+Ack Packet
      GBNSegment hsSynAck = GBNSegment.createHandshakeSegment(bsNumReceiver, HandshakeType.SYNACK);
      byte[] hsSynAckBytes = hsSynAck.serialize();
      DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, hsSynAckBytes.length,
          handshakeSynPacket.getAddress(), handshakeSynPacket.getPort());
      System.out.println("Rcvr - send syn+ack chk: " + handshakeSyn.getChecksum());
      bsNumReceiver++;
      receiverSocket.send(hsSynAckPacket);

      // Receive Ack Packet (3rd leg)
      byte[] hsAckBytes = new byte[mtu];
      DatagramPacket hsAckUdp = new DatagramPacket(hsAckBytes, mtu);
      receiverSocket.receive(hsAckUdp);
      hsAckBytes = hsAckUdp.getData();
      GBNSegment hsAck = new GBNSegment();
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
      // TODO: handle case where ACK handshake packet is dropped
      // (5.2 "First, if the client's ACK to the server is lost...")
      if (!hsAck.isAck || hsAck.isFin || hsAck.isSyn) {
        System.out.println("Handshake: Rcvr - 3rd segment doesn't have correct flags!");
      }

      receiverSocket.close();
    } else {
      System.out.println(
          "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
    }
  }
}
