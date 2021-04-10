import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class TCPEnd {

  long INITIAL_TIMEOUT = 5000000000L; // initial timeout in nanoseconds
  static DecimalFormat threePlaces = new DecimalFormat("0.000");
  static int mtu = -1;
  static int sws = -1;

  public static void main(String[] args) throws IOException {
    int senderSourcePort = -1;
    int receiverPort = -1;
    InetAddress receiverIp = null;
    String filename = null;

    int bsNumSender = 0; // TODO: move to Sender class
    int sndNextByteExpected = 0;
    int bsNumReceiver = 0; // TODO: move to Receiver class

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
      GBNSegment handshakeSyn = GBNSegment.createHandshakeSegment(bsNumSender, HandshakeType.SYN);
      byte[] handshakeSynData = handshakeSyn.serialize();
      DatagramPacket handshakeSynPacket =
          new DatagramPacket(handshakeSynData, handshakeSynData.length, receiverIp, receiverPort);
      System.out.println("Handshake: Sender first syn chk: " + handshakeSyn.getChecksum());
      senderSocket.send(handshakeSynPacket);
      bsNumSender++;

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
      sndNextByteExpected = hsSynAck.byteSequenceNum + 1;

      // Send 3rd Ack Packet
      GBNSegment hsAck = GBNSegment.createHandshakeSegment(bsNumSender, HandshakeType.ACK);
      byte[] hsAckBytes = hsAck.serialize();
      DatagramPacket hsAckUdp =
          new DatagramPacket(hsAckBytes, hsAckBytes.length, receiverIp, receiverPort);
      System.out.println("Handshake: Sndr - ack chk: " + hsAck.getChecksum());
      senderSocket.send(hsAckUdp);

      // Data Transfer
      try (InputStream in = new FileInputStream(filename)) {
        // inputStream = new BufferedReader(new FileReader(filename), mtu);
        DataInputStream inputStream = new DataInputStream(in);

        // ArrayList<byte[]> sendBuffer = new ArrayList<>();
        byte[] sendBuffer = new byte[mtu * sws]; // right now, buffer is the same size as the sws

        // byte[] onePayloadData = new byte[mtu];

        // Initial filling up send buffer
        int lastByteSent = 0;
        int lastByteAcked = 0; // TODO: or could be 1 after SYN
        int lastByteWritten = 0;
        int effectiveWindow = 0;
        int advertisedWindow = sws;
        // int maxSenderBuffer = sws * 10; // TODO: right now, buffer = sws
        int byteReadCount;

        // fill up entire sendbuffer, which is currently = sws
        while ((byteReadCount = inputStream.read(sendBuffer, 0, mtu * sws)) != -1) {
          System.out.println("TCPEnd Sender - byteReadCount: " + byteReadCount + "lastByteWritten: " + lastByteWritten);
          lastByteWritten += byteReadCount;

          // send entire buffer (currently = sws)
          for (int j = 0; j < sws; j++) {
            // create and send one segment
            byte[] onePayload = new byte[mtu];
            onePayload = Arrays.copyOfRange(sendBuffer, j * mtu, (j + 1) * mtu);

            GBNSegment dataSegment =
                GBNSegment.createDataSegment(bsNumSender, sndNextByteExpected, onePayload);
            byte[] dataSegmentBytes = dataSegment.serialize();
            DatagramPacket dataPacket = new DatagramPacket(dataSegmentBytes,
                dataSegmentBytes.length, receiverIp, receiverPort);
            senderSocket.send(dataPacket);
            printOutputSend(dataSegment);
            lastByteSent += mtu;
            bsNumSender = lastByteSent; // these are currently redundant
          }

          // wait for acks
          while (lastByteAcked < lastByteSent) {
            // receive
            GBNSegment ack = handlePacket(senderSocket);
            if (!ack.isAck) { // TODO: handle fin segment
              System.out.println("Error: TCPEnd receiver sent something other than an ack");
            }

            // piazza@393_f2 AckNum == NextByteExpected == LastByteAcked + 1
            lastByteAcked = ack.getAckNum() - 1;

            // retransmit (timeout or three duplicate acks)
          }

          // remove from buffer
        }
        // while (((byteReadCount = inputStream.read(onePayloadData, lastByteWritten, mtu)) != -1)
        // && (lastByteWritten - lastByteAcked <= maxSenderBuffer)) {
        // sendBuffer.add(onePayloadData);
        // lastByteWritten += byteReadCount;
        // }
        // do {
        // // Write to Send Buffer "Application" Code
        // while (((byteReadCount = inputStream.read(onePayloadData, lastByteWritten, mtu)) != -1)
        // && (lastByteWritten - lastByteAcked <= maxSenderBuffer)) {
        // sendBuffer.add(onePayloadData);
        // lastByteWritten += byteReadCount;
        // }
        //
        // effectiveWindow = advertisedWindow - (lastByteSent - lastByteAcked);
        //
        // // Nagle's Algorithm
        //
        // } while (sendBuffer.size() > 0);
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
      InetAddress tcpSenderIpAddress = handshakeSynPacket.getAddress();
      int tcpSenderPort = handshakeSynPacket.getPort();


      // Send 2nd Syn+Ack Packet
      GBNSegment hsSynAck = GBNSegment.createHandshakeSegment(bsNumReceiver, HandshakeType.SYNACK);
      byte[] hsSynAckBytes = hsSynAck.serialize();
      DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, hsSynAckBytes.length,
          tcpSenderIpAddress, tcpSenderPort);
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

      try (OutputStream out = new FileOutputStream(filename, true)) {
        DataOutputStream outStream = new DataOutputStream(out);

        boolean isOpen = true;
        int nextByteExpected = 0;
        int lastByteReceived = 0; // currently redundant as long as discarding out-of-order pkt
        int lastByteRead = 0;
        while (isOpen) {
          // receive data
          GBNSegment data = handlePacket(receiverSocket);
          if (!data.isAck || data.getDataLength() <= 0) {
            // TODO: handle fin
            // terminate connection and set isOpen to false
          }
          // TODO: discard out-of-order packets (and send duplicate ack)

          // reconstruct file
          outStream.write(data.getPayload());

          // send ack
          lastByteReceived += data.getDataLength();
          nextByteExpected = lastByteReceived + 1;
          GBNSegment ackSegment = GBNSegment.createAckSegment(bsNumReceiver, nextByteExpected);
          byte[] ackBytes = ackSegment.serialize();
          DatagramPacket ackPacket =
              new DatagramPacket(ackBytes, ackBytes.length, tcpSenderIpAddress, tcpSenderPort);
          printOutputSend(ackSegment);
          receiverSocket.send(ackPacket);
        }
      } catch (Exception e) {
        // TODO: handle exception
      }

      receiverSocket.close();
    } else {
      System.out.println(
          "Sender: java TCPend -p <port> -s <remote IP> -a <remote port> -f <file name> -m <mtu> -c <sws>");
      System.out.println("Receiver: java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
    }
  }

  public static GBNSegment handlePacket(DatagramSocket rcvSocket) throws IOException {
    // Receive First Syn Packet
    byte[] bytes = new byte[mtu];
    DatagramPacket packet = new DatagramPacket(bytes, mtu);
    rcvSocket.receive(packet);
    bytes = packet.getData();
    GBNSegment segment = new GBNSegment();
    segment.deserialize(bytes);

    // Verify checksum first syn packet
    short origChk = segment.getChecksum();
    segment.resetChecksum();
    segment.serialize();
    short calcChk = segment.getChecksum();
    if (origChk != calcChk) {
      System.out.println("Rcvr - chk does not match!");
    }

    double currTime = System.nanoTime() / 1000000000;

    System.out.print("rcv " + threePlaces.format(currTime));
    System.out.print(segment.isSyn ? " S" : " -");
    System.out.print(segment.isAck ? " A" : " -");
    System.out.print(segment.isFin ? " F" : " -");
    System.out.print((segment.getDataLength() > 0) ? " D" : " -");
    System.out.print(" " + segment.byteSequenceNum);
    System.out.print(" " + segment.getDataLength());
    System.out.print(" " + segment.ackNum);
    System.out.println();

    return segment;
  }

  public static void printOutputSend(GBNSegment segment) {
    double currTime = System.nanoTime() / 1000000000;

    System.out.print("snd " + threePlaces.format(currTime));
    System.out.print(segment.isSyn ? " S" : " -");
    System.out.print(segment.isAck ? " A" : " -");
    System.out.print(segment.isFin ? " F" : " -");
    System.out.print((segment.getDataLength() > 0) ? " D" : " -");
    System.out.print(" " + segment.byteSequenceNum);
    System.out.print(" " + segment.getDataLength());
    System.out.print(" " + segment.ackNum);
    System.out.println();
  }

  // public static void sendPacket(DatagramSocket sndSocket, GBNSegment segment) {
  //
  // }

}
