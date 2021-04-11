import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Receiver extends TCPEndHost{
  protected InetAddress senderIp;
  protected int senderPort;
  
  public Receiver(int receiverPort, String filename, int mtu, int sws) {
    this.receiverPort = receiverPort;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
  }
  
  public void openConnection() throws IOException {
    this.socket = new DatagramSocket(receiverPort);

    // Receive First Syn Packet
    byte[] bytes = new byte[mtu];
    DatagramPacket handshakeSynPacket = new DatagramPacket(bytes, mtu);
    socket.receive(handshakeSynPacket);
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
    senderIp = handshakeSynPacket.getAddress();
    senderPort = handshakeSynPacket.getPort();

    // Send 2nd Syn+Ack Packet
    GBNSegment hsSynAck = GBNSegment.createHandshakeSegment(bsn, HandshakeType.SYNACK);
    byte[] hsSynAckBytes = hsSynAck.serialize();
    DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, hsSynAckBytes.length,
        senderIp, senderPort);
    System.out.println("Rcvr - send syn+ack chk: " + handshakeSyn.getChecksum());
    bsn++;
    socket.send(hsSynAckPacket);

    // Receive Ack Packet (3rd leg)
    byte[] hsAckBytes = new byte[mtu];
    DatagramPacket hsAckUdp = new DatagramPacket(hsAckBytes, mtu);
    socket.receive(hsAckUdp);
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
  }
  
  public void receiveData() {
    try (OutputStream out = new FileOutputStream(filename, true)) {
      DataOutputStream outStream = new DataOutputStream(out);

      boolean isOpen = true;
      int nextByteExpected = 0;
      int lastByteReceived = 0; // currently redundant as long as discarding out-of-order pkt
      int lastByteRead = 0;
      while (isOpen) {
        // receive data
        GBNSegment data = handlePacket(socket);
        if (!data.isAck || data.getDataLength() <= 0) {
          // TODO: handle fin
          // terminate connection and set isOpen to false
        }
        // TODO: discard out-of-order packets (and send duplicate ack)

        // reconstruct file
        System.out.println("TCPEnd Rcvr - act len: " + data.getPayload().length + ", exp len: "
            + data.getDataLength());
        outStream.write(data.getPayload());

        // send ack
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
      // TODO: handle exception
    }
  }

}
