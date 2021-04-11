import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver extends TCPEndHost {
  protected InetAddress senderIp;
  protected int senderPort;

  public Receiver(int receiverPort, String filename, int mtu, int sws) {
    this.receiverPort = receiverPort;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
  }

  public void openConnection() {
    try {
      this.socket = new DatagramSocket(receiverPort);

      // Receive First Syn Packet
      byte[] bytes = new byte[mtu];
      DatagramPacket handshakeSynPacket = new DatagramPacket(bytes, mtu);
      socket.receive(handshakeSynPacket);
      byte[] handshakeSynBytes = handshakeSynPacket.getData();
      GBNSegment handshakeSyn = new GBNSegment();
      handshakeSyn.deserialize(handshakeSynBytes);

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
      senderIp = handshakeSynPacket.getAddress();
      senderPort = handshakeSynPacket.getPort();

      // Send 2nd Syn+Ack Packet
      GBNSegment hsSynAck = GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.SYNACK);
      byte[] hsSynAckBytes = hsSynAck.serialize();
      DatagramPacket hsSynAckPacket =
          new DatagramPacket(hsSynAckBytes, hsSynAckBytes.length, senderIp, senderPort);
      bsn++;
      socket.send(hsSynAckPacket);

      // Receive Ack Packet (3rd leg)
      byte[] hsAckBytes = new byte[mtu];
      DatagramPacket hsAckUdp = new DatagramPacket(hsAckBytes, mtu);
      socket.receive(hsAckUdp);
      hsAckBytes = hsAckUdp.getData();
      GBNSegment hsAck = new GBNSegment();
      hsAck.deserialize(hsAckBytes);

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
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void receiveDataAndClose() {
    try (OutputStream out = new FileOutputStream(filename)) {
      DataOutputStream outStream = new DataOutputStream(out);

      boolean isOpen = true;
      int nextByteExpected = 0;
      int lastByteReceived = 0; // currently redundant as long as discarding out-of-order pkt
      int lastByteRead = 0;
      while (isOpen) {
        // Receive data
        GBNSegment data = handlePacket(socket);
        if (!data.isAck || data.getDataLength() <= 0) {
          // Set isOpen to false
          if (data.isFin) {
            isOpen = false;
            // Terminate connection
            // Send ACK
            // TODO: retransmit ACK
            GBNSegment returnAckSegment = GBNSegment.createAckSegment(bsn, nextByteExpected);
            sendPacket(returnAckSegment, senderIp, senderPort);
            // Send FIN
            GBNSegment returnFinSegment = GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.FIN);
            sendPacket(returnFinSegment, senderIp, senderPort);
            bsn++;
            
            // Recieve last ACK
            GBNSegment lastAckSegment = handlePacket(socket);
            if (!lastAckSegment.isAck || lastAckSegment.isFin || lastAckSegment.isSyn) {
              System.out.println("Error: Rcv - unexpected flags!");
            }
            socket.close();
            break;
          }
        }
        // TODO: discard out-of-order packets (and send duplicate ack)

        // Reconstruct file
        outStream.write(data.getPayload());

        // Send ack
        lastByteReceived += data.getDataLength();
        nextByteExpected = lastByteReceived + 1;
        GBNSegment ackSegment = GBNSegment.createAckSegment(bsn, nextByteExpected);
        byte[] ackBytes = ackSegment.serialize();
        DatagramPacket ackPacket =
            new DatagramPacket(ackBytes, ackBytes.length, senderIp, senderPort);
        printOutput(ackSegment, true);
        socket.send(ackPacket);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }    
  }

}
