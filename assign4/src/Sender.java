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

  public void openConnection() throws IOException, MaxRetransmitException {
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
        GBNSegment handshakeSecondSynAck;
        try {
          handshakeSecondSynAck = handlePacket();
        } catch (SegmentChecksumMismatchException e) {
          e.printStackTrace();
          this.numDiscardPackets++;
          continue;
        }

        if (handshakeSecondSynAck.isSyn && handshakeSecondSynAck.isAck) {
          nextByteExpected++;
          isSynAckReceived = true;

          // Send 3rd Ack Packet
          GBNSegment handshakeThirdAck =
              GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.ACK);
          sendPacket(handshakeThirdAck, receiverIp, receiverPort);
        } else {
          this.numRetransmits++;
          if (this.numRetransmits >= (MAX_RETRANSMITS + 1)) {
            throw new MaxRetransmitException("Max SYN retransmits!");
          }
          bsn--;
          continue;
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for SYNACK!");
        this.numRetransmits++;
        if (this.numRetransmits % (MAX_RETRANSMITS + 1) == 0) {
          throw new MaxRetransmitException("Max SYN retransmits!");
        }
        bsn--;
        continue;
      }
    }
  }

  public void sendData() throws MaxRetransmitException {
    // Data Transfer
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename))) {
      DataInputStream inputStream = new DataInputStream(in);
      byte[] sendBuffer = new byte[mtu * sws]; // right now, buffer is the same size as the sws

      // Initial filling up send buffer
      int lastByteAcked = 0;
      int lastByteWritten = 0;
      short currRetransmit = 0;
      int byteReadCount;
      int currDupAck = 0;

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
            GBNSegment currAck;
            try {
              currAck = handlePacket();

            if (!currAck.isAck) {
              throw new UnexpectedFlagException("Expected ACK!", currAck);
            }
            this.socket.setSoTimeout((int) (timeout / 1000000));

            // piazza@393_f2 AckNum == NextByteExpected == LastByteAcked + 1
            int prevAck = lastByteAcked;
            lastByteAcked = currAck.ackNum - 1;

            // Retransmit (three duplicate acks)
            if (prevAck == lastByteAcked) {
              currDupAck++;
              this.numDupAcks++;
              if (currDupAck == 3) {
                if (currRetransmit >= MAX_RETRANSMITS) {
                  throw new MaxRetransmitException("Max data retransmits!");
                }
                // Slide the window
                // skip bytes from lastAck to mark (start of buffer in read loop)
                slideWindow(inputStream, lastByteAcked, lastByteWritten, byteReadCount);
                lastByteWritten = this.lastByteSent;
                currRetransmit++;
                break; // exit wait ACK loop
              }
            } else {
              currDupAck = 0;
            }
          } catch (SocketTimeoutException e) {
            System.err.println("Timeout while waiting for ACK!");
            if (currRetransmit >= MAX_RETRANSMITS) {
              throw new MaxRetransmitException("Max data retransmits!");
            }
            // Slide the window and retransmit after timeout
            slideWindow(inputStream, lastByteAcked, lastByteWritten, byteReadCount);
            lastByteWritten = this.lastByteSent;
            currRetransmit++;
            break; // exit wait ACK loop
          } catch (UnexpectedFlagException e) {
            e.printStackTrace();
            this.numDiscardPackets++;
            continue;
          } catch (SegmentChecksumMismatchException e) {
            e.printStackTrace();
            this.numDiscardPackets++;
            continue;
          }
          // reset counter because we made it through the window without retrasmission
          currRetransmit = 0;
        }

        // remove from buffer; mark position in file
        inputStream.mark(mtu * sws);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void slideWindow(DataInputStream inputStream, int lastByteAcked, int lastByteWritten,
      int byteReadCount) throws IOException {
    inputStream.reset();
    inputStream.skip(lastByteAcked - (lastByteWritten - byteReadCount));
    this.lastByteSent = lastByteAcked;
    this.bsn = lastByteAcked + 1;
    this.numRetransmits++;
  }

  public void closeConnection()
      throws IOException, MaxRetransmitException, UnexpectedFlagException {
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
        GBNSegment returnFinAckSegment = null;
        do {
          // we still might be receiving leftover ACKs from data transfer
          try {
            returnFinAckSegment = handlePacket();
          } catch (SegmentChecksumMismatchException e) {
            e.printStackTrace();
            this.numDiscardPackets++;
            bsn--;
            continue;
          }
        } while (returnFinAckSegment.isAck && !returnFinAckSegment.isFin && !returnFinAckSegment.isSyn);

        if (returnFinAckSegment.isFin && returnFinAckSegment.isAck && !returnFinAckSegment.isSyn) {
          nextByteExpected++;
          isFinAckReceived = true;

          // Send last ACK
          GBNSegment lastAckSegment =
              GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.ACK);
          sendPacket(lastAckSegment, receiverIp, receiverPort);
        } else {
          throw new UnexpectedFlagException();
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for FINACK!");
        currNumRetransmits++;
        if (currNumRetransmits >= (MAX_RETRANSMITS + 1)) {
          throw new MaxRetransmitException("Max FIN retransmits!");
        }
        this.numRetransmits++;
        bsn--;
        continue;
      }
    }
  }

  public void printFinalStatsHeader() {
    System.out.println("TCPEnd Sender Finished==========");
    this.printFinalStats();
  }

}
