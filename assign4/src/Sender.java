import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class Sender extends TCPEndHost {

  public Sender(int senderSourcePort, InetAddress receiverIp, int receiverPort, String filename, int mtu, int sws) {
    this.senderSourcePort = senderSourcePort;
    this.receiverIp = receiverIp;
    this.receiverPort = receiverPort;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
  }
  
  public void openConnection() throws IOException {
    this.socket = new DatagramSocket(senderSourcePort);
    // byte[] bytes = new byte[5];
    // DatagramPacket packet = new DatagramPacket(bytes, 5, receiverIp, receiverPort);

    // TODO: Retransmission of handshake (hopefully same implementation as data transfer)
    // piazza@395
    // TODO: Check flags
    // TODO: does SYN flag occupy one byte in byte sequence number? piazza@###
    // Send First Syn Packet
    // TODO: fix setting mtu to less than TCP segment size BufferUnderflowException
    GBNSegment handshakeSyn = GBNSegment.createHandshakeSegment(bsn, HandshakeType.SYN);
    byte[] handshakeSynData = handshakeSyn.serialize();
    DatagramPacket handshakeSynPacket =
        new DatagramPacket(handshakeSynData, handshakeSynData.length, receiverIp, receiverPort);
    System.out.println("Handshake: Sender first syn chk: " + handshakeSyn.getChecksum());
    socket.send(handshakeSynPacket);
    bsn++;

    // Receive 2nd Syn+Ack Packet
    byte[] hsSynAckBytes = new byte[mtu];
    DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, mtu);
    socket.receive(hsSynAckPacket);
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
    nextByteExpected = hsSynAck.byteSequenceNum + 1;

    // Send 3rd Ack Packet
    GBNSegment hsAck = GBNSegment.createHandshakeSegment(bsn, HandshakeType.ACK);
    byte[] hsAckBytes = hsAck.serialize();
    DatagramPacket hsAckUdp =
        new DatagramPacket(hsAckBytes, hsAckBytes.length, receiverIp, receiverPort);
    System.out.println("Handshake: Sndr - ack chk: " + hsAck.getChecksum());
    socket.send(hsAckUdp);
  }
  
  public void sendData() throws IOException {
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
      int oldLastByteWritten = 0;
      int effectiveWindow = 0;
      int advertisedWindow = sws;
      // int maxSenderBuffer = sws * 10; // TODO: right now, buffer = sws
      int byteReadCount;

      // fill up entire sendbuffer, which is currently = sws
      while ((byteReadCount = inputStream.read(sendBuffer, 0, mtu * sws)) != -1) {
        oldLastByteWritten = lastByteWritten;
        // send entire buffer (currently = sws)
        // TODO: handle end of file better (it keeps sending on the last iteration even though the
        // file is empty)
        // TODO: implement buffer that is larger than sws
        // TODO: discard packets due to incorrect checksum
        for (int j = 0; j < sws; j++) {
          byte[] onePayload;
          int lastPayloadLength = byteReadCount % mtu;
          if (lastPayloadLength != 0) {
            onePayload = new byte[lastPayloadLength];
            onePayload = Arrays.copyOfRange(sendBuffer, j * mtu, (j * mtu) + lastPayloadLength);
            lastByteWritten += lastPayloadLength;
          } else if (lastByteWritten == oldLastByteWritten + byteReadCount) {
            break;
          } else {
            onePayload = new byte[mtu];
            onePayload = Arrays.copyOfRange(sendBuffer, j * mtu, (j + 1) * mtu);
            lastByteWritten += mtu;
          }

          GBNSegment dataSegment =
              GBNSegment.createDataSegment(bsn, nextByteExpected, onePayload);
          byte[] dataSegmentBytes = dataSegment.serialize();
          DatagramPacket dataPacket = new DatagramPacket(dataSegmentBytes,
              dataSegmentBytes.length, receiverIp, receiverPort);
          System.out.println("TCPEndSender - dataPacket datalen: " + dataPacket.getLength());
          socket.send(dataPacket);
          printOutput(dataSegment, true);
          lastByteSent += mtu;
          bsn = lastByteSent; // these are currently redundant
        }

        // wait for acks
        while (lastByteAcked < lastByteSent) {
          // receive
          GBNSegment ack = handlePacket(socket);
          if (!ack.isAck) { // TODO: handle fin segment
            System.out.println("Error: TCPEnd receiver sent something other than an ack");
          }

          // piazza@393_f2 AckNum == NextByteExpected == LastByteAcked + 1
          lastByteAcked = ack.getAckNum() - 1;

          // TODO: retransmit (timeout)
          // TODO: retransmit (three duplicate acks)

          // TODO: Nagle's algorithm?
        }

        // remove from buffer
      }
    }
//
  }

}
