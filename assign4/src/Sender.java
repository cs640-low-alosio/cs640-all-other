import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class Sender extends TCPEndHost {

  public Sender(int senderSourcePort, InetAddress receiverIp, int receiverPort, String filename,
      int mtu, int sws) {
    this.senderSourcePort = senderSourcePort;
    this.receiverIp = receiverIp;
    this.receiverPort = receiverPort;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
  }

  public void openConnection() {
    try {
      this.socket = new DatagramSocket(senderSourcePort);

      // Send First Syn Packet
      // TODO: Retransmission of handshake (hopefully same implementation as data transfer)
      // piazza@395
      // TODO: Check flags
      // TODO: does SYN flag occupy one byte in byte sequence number? piazza@###
      // TODO: fix setting mtu to less than TCP segment size BufferUnderflowException
      GBNSegment handshakeSyn = GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.SYN);
      byte[] handshakeSynData = handshakeSyn.serialize();
      DatagramPacket handshakeSynPacket =
          new DatagramPacket(handshakeSynData, handshakeSynData.length, receiverIp, receiverPort);
      socket.send(handshakeSynPacket);
      bsn++;

      // Receive 2nd Syn+Ack Packet
      byte[] hsSynAckBytes = new byte[mtu];
      DatagramPacket hsSynAckPacket = new DatagramPacket(hsSynAckBytes, mtu);
      socket.receive(hsSynAckPacket);
      hsSynAckBytes = hsSynAckPacket.getData();
      GBNSegment hsSynAck = new GBNSegment();
      hsSynAck.deserialize(hsSynAckBytes);
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
      GBNSegment hsAck = GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.ACK);
      byte[] hsAckBytes = hsAck.serialize();
      DatagramPacket hsAckUdp =
          new DatagramPacket(hsAckBytes, hsAckBytes.length, receiverIp, receiverPort);
      socket.send(hsAckUdp);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void sendData() {
    // Data Transfer
    try (InputStream in = new FileInputStream(filename)) {
      DataInputStream inputStream = new DataInputStream(in);
      byte[] sendBuffer = new byte[mtu * sws]; // right now, buffer is the same size as the sws

      // Initial filling up send buffer
      int lastByteSent = 0;
      int lastByteAcked = 0;
      int lastByteWritten = 0;
      int tempLastByteWritten = 0;
      int effectiveWindow = 0;
      int advertisedWindow = sws;
      int byteReadCount;

      // fill up entire sendbuffer, which is currently = sws
      while ((byteReadCount = inputStream.read(sendBuffer, 0, mtu * sws)) != -1) {
        lastByteWritten += byteReadCount;
        // Send entire buffer (currently = sws)
        // TODO: handle end of file better (it keeps sending on the last iteration even though the
        // file is empty)
        // TODO: implement sws during retransmit
        // TODO: discard packets due to incorrect checksum
        for (int j = 0; j < (byteReadCount / mtu) + 1; j++) {
          byte[] onePayload;
          int payloadLength;
          if (j == byteReadCount / mtu) { // last payload
            payloadLength = byteReadCount % mtu;
            if (payloadLength != 0) {
              payloadLength = byteReadCount % mtu;
            } else { // last payload is 0
              break;
            }
          } else {
            payloadLength = mtu;
          }
          onePayload = new byte[payloadLength];
          onePayload = Arrays.copyOfRange(sendBuffer, j * mtu, (j * mtu) + payloadLength);

          GBNSegment dataSegment = GBNSegment.createDataSegment(bsn, nextByteExpected, onePayload);
          byte[] dataSegmentBytes = dataSegment.serialize();
          DatagramPacket dataPacket = new DatagramPacket(dataSegmentBytes, dataSegmentBytes.length,
              receiverIp, receiverPort);
          socket.send(dataPacket);
          printOutput(dataSegment, true);
          lastByteSent += payloadLength;
          bsn += payloadLength;
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
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void closeConnection() {
    // Send FIN
    // TODO: retransmit fin
    GBNSegment finSegment = GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.FIN);
    sendPacket(finSegment, receiverIp, receiverPort);
    bsn++;

    // Receive ACK
    GBNSegment returnAckSegment = handlePacket(socket);
    if (!returnAckSegment.isAck || returnAckSegment.isFin || returnAckSegment.isSyn) {
      System.out.println("Error: Snd - unexpected flags!");
    }
    // Receive FIN
    GBNSegment returnFinSegment = handlePacket(socket);
    if (!returnFinSegment.isFin || returnFinSegment.isAck || returnFinSegment.isSyn) {
      System.out.println("Error: Snd - unexpected flags!");
    }
    nextByteExpected++;

    // Send last ACK
    GBNSegment lastAckSegment = GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.ACK);
    sendPacket(lastAckSegment, receiverIp, receiverPort);

    // TODO: wait timeout to close connection (see lecture/book)
    // The main thing to recognize about connection teardown is that a connection in the TIME_WAIT
    // state cannot move to the CLOSED state until it has waited for two times the maximum amount of
    // time an IP datagram might live in the Internet (i.e., 120 seconds). The reason for this is
    // that, while the local side of the connection has sent an ACK in response to the other side's
    // FIN segment, it does not know that the ACK was successfully delivered. As a consequence, the
    // other side might retransmit its FIN segment, and this second FIN segment might be delayed in
    // the network. If the connection were allowed to move directly to the CLOSED state, then
    // another pair of application processes might come along and open the same connection (i.e.,
    // use the same pair of port numbers), and the delayed FIN segment from the earlier incarnation
    // of the connection would immediately initiate the termination of the later incarnation of that
    // connection.
  }

}
