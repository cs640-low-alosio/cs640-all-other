import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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

  public boolean openConnection() throws IOException {
    this.socket = new DatagramSocket(senderSourcePort);
    this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);

    // Send First Syn Packet
    // piazza@395
    boolean isSynAckReceived = false;
    while (!isSynAckReceived) {
      GBNSegment handshakeFirstSyn =
          GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.SYN);
      sendPacket(handshakeFirstSyn, receiverIp, receiverPort);
      bsn++;

      // // Receive 2nd Syn+Ack Packet
      try {
        GBNSegment handshakeSecondSynAck = handlePacket();
        if (handshakeSecondSynAck.isSyn && handshakeSecondSynAck.isAck) {
          nextByteExpected++;
          isSynAckReceived = true;
        } else {
          System.out.println("Error: expected SYNACK packet, got something else!");
          bsn--;
          continue;
        }

        // Send 3rd Ack Packet
        GBNSegment handshakeThirdAck =
            GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.ACK);
        sendPacket(handshakeThirdAck, receiverIp, receiverPort);
      } catch (SocketTimeoutException e) {
        this.numRetransmits++;
        if (this.numRetransmits % 17 == 0) {
          // exit immediately
          System.out.println("Max SYN retransmits!");
          return true;
        }
        bsn--;
        System.out.println("Retransmit SYN! " + this.numRetransmits);
        continue;
      }
    }

    return false;
  }

  public void sendData() {
    // Data Transfer
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename))) {
      DataInputStream inputStream = new DataInputStream(in);
      byte[] sendBuffer = new byte[mtu * sws]; // right now, buffer is the same size as the sws

      // Initial filling up send buffer
      int lastByteAcked = 0;
      int lastByteWritten = 0;
      short retransmitCounter = 0;
      int byteReadCount;
      int dupAckCount = 0;

      inputStream.mark(mtu * sws);
      // fill up entire sendbuffer, which is currently = sws
      while ((byteReadCount = inputStream.read(sendBuffer, 0, mtu * sws)) != -1) {
        lastByteWritten += byteReadCount;
        // Send entire buffer (currently = sws)
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
          sendPacket(dataSegment, receiverIp, receiverPort);
          this.lastByteSent += payloadLength; // lastbytesent is the same as lastbytewritten?
          bsn += payloadLength;
        }

        // wait for ACKs
        while (lastByteAcked < this.lastByteSent) {
          try {
            GBNSegment currAck = handlePacket();
            if (!currAck.isAck) {
              System.out.println("Error: Snd - unexpected flags!");
            }
            this.socket.setSoTimeout((int) (timeout / 1000000));

            // piazza@393_f2 AckNum == NextByteExpected == LastByteAcked + 1
            int prevAck = lastByteAcked;
            lastByteAcked = currAck.ackNum - 1;

            // Retransmit (three duplicate acks)
            // TODO: don't care about ACKs from before either - unresolved question piazza@458
            // TODO: Max retransmit counter for three duplicate ACKs
            if (prevAck == lastByteAcked) {
              dupAckCount++;
              this.numDupAcks++;
              if (dupAckCount >= 3) {
                // TODO: Beyond 3 duplicate ACKs, do we retransmit every single time?
                System.out.println("Snd - Dup Ack Retransmit! # retransmit: " + numRetransmits);
                inputStream.reset();
                // Slide the window
                // skip bytes from lastAck to mark (start of buffer in read loop)
                inputStream.skip(lastByteAcked - (lastByteWritten - byteReadCount));
                lastByteWritten = lastByteAcked;
                this.lastByteSent = lastByteAcked;
                this.bsn = lastByteAcked + 1;
                this.numRetransmits++;
                break; // exit wait ACK loop
              }
            } else {
              dupAckCount = 0;
            }
          } catch (SocketTimeoutException e) {
            // If unacknowledged messages remain in a host's send buffer and no response from the
            // destination has been received after multiple retransmission attempts, the sending
            // host will stop trying to send the messages and report an error. This maximum is set
            // to 16 by default.
            // TODO: exit completely
            // TODO: java.io.IOException: Network is unreachable
            // link h1 r1 down
            if (retransmitCounter >= 16) {
              System.out.println("Already sent 16 retransmits. Quitting!");
              e.printStackTrace();
              return;
            }
            // Slide the window
            // Redundant code with triplicate ACK
            inputStream.reset();
            inputStream.skip(lastByteAcked - (lastByteWritten - byteReadCount));
            lastByteWritten = lastByteAcked;
            this.lastByteSent = lastByteAcked;
            this.bsn = lastByteAcked + 1;
            System.out.println("Snd - TO Retransmit! # curr retransmit: " + retransmitCounter);
            retransmitCounter++;
            this.numRetransmits++;
            break; // exit wait ACK loop
          }
          // reset counter because we made it through the window without retrasmission
          retransmitCounter = 0;
        }

        // remove from buffer
        // position in file
        inputStream.mark(mtu * sws);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public boolean closeConnection() throws IOException {
    // Send FIN
    boolean isFinAckReceived = false;
    short currNumRetransmits = 0;
    while (!isFinAckReceived) {
      GBNSegment finSegment =
          GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.FIN);
      sendPacket(finSegment, receiverIp, receiverPort);
      bsn++;

      // Receive FIN+ACK
      try {
        GBNSegment returnFinSegment = handlePacket();
        if (!(returnFinSegment.isFin && returnFinSegment.isAck) || returnFinSegment.isSyn) {
          System.out.println("Error: Snd - unexpected flags!");
        }
        nextByteExpected++;
        isFinAckReceived = true;

        // Send last ACK
        GBNSegment lastAckSegment =
            GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.ACK);
        sendPacket(lastAckSegment, receiverIp, receiverPort);
      } catch (SocketTimeoutException e) {
        currNumRetransmits++;
        if (currNumRetransmits >= 17) {
          // exit immediately after 16 retransmit attempts
          System.out.println("Max FIN retransmits!");
          return true;
        }
        System.out.println("retransmit FIN! " + currNumRetransmits);
        this.numRetransmits++;
        bsn--;
        continue;
      }
    }
    return false;
  }

  public void printFinalStatsHeader() {
    System.out.println("TCPEnd Sender Finished==========");
    this.printFinalStats();
  }

}
