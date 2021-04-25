import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.PriorityQueue;

public class Receiver extends TCPEndHost {
  protected InetAddress senderIp;
  protected int senderPort;

  public Receiver(int receiverPort, String filename, int mtu, int sws) {
    this.receiverPort = receiverPort;
    this.filename = filename;
    this.mtu = mtu;
    this.sws = sws;
    this.lastByteReceived = 0;
    this.numPacketsSent = 0;
    this.numPacketsReceived = 0;
  }

  public GBNSegment openConnection() throws IOException, MaxRetransmitException,
      SegmentChecksumMismatchException, UnexpectedFlagException {
    GBNSegment firstReceivedAck = null;

    // this.socket = new DatagramSocket(receiverPort);
    // this.socket.setSoTimeout(0);

    // Receive First Syn Packet
    // Do this manually to get the sender IP and port
    byte[] bytes = new byte[mtu + GBNSegment.HEADER_LENGTH_BYTES];
    DatagramPacket handshakeSynPacket =
        new DatagramPacket(bytes, mtu + GBNSegment.HEADER_LENGTH_BYTES);
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
      throw new SegmentChecksumMismatchException();
    }
    if (handshakeSyn.isSyn && !handshakeSyn.isAck && !handshakeSyn.isFin) {
      printOutput(handshakeSyn, false);
      senderIp = handshakeSynPacket.getAddress();
      senderPort = handshakeSynPacket.getPort();
      nextByteExpected++;
      numPacketsReceived++;
    } else {
      throw new UnexpectedFlagException("Expected SYN flags!", handshakeSyn);
    }

    boolean isFirstAckReceived = false;
    while (!isFirstAckReceived) {
      try {
        // Send 2nd Syn+Ack Packet
        GBNSegment handshakeSynAck =
            GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.SYNACK);
        sendPacket(handshakeSynAck, senderIp, senderPort);
        bsn++;

        // Receive Ack Packet (3rd leg)
        do {
          // we might still be receiving leftover SYN retransmits
          try {
            socket.setSoTimeout(INITIAL_TIMEOUT_MS);
            firstReceivedAck = handlePacket();
          } catch (SegmentChecksumMismatchException e) {
            e.printStackTrace();
            this.numChkDiscardPackets++;
            continue;
          }
        } while (firstReceivedAck.isSyn);

        if (firstReceivedAck.isAck && !firstReceivedAck.isFin && !firstReceivedAck.isSyn) {
          isFirstAckReceived = true;
        } else {
          throw new UnexpectedFlagException();
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for first ACK!");
        this.numRetransmits++;
        if (this.numRetransmits % (MAX_RETRANSMITS + 1) == 0) {
          // exit immediately
          throw new MaxRetransmitException("Max SYNACK retransmits!");
        }
        bsn--;
        continue;
      }
    }

    if (firstReceivedAck != null && firstReceivedAck.dataLength >= 0) {
      return firstReceivedAck;
    } else {
      return null;
    }
  }

  public void receiveDataAndClose(GBNSegment firstReceivedAck) throws MaxRetransmitException {
    try (OutputStream out = new FileOutputStream(filename)) {
      DataOutputStream outStream = new DataOutputStream(out);

      boolean isOpen = true;
      // out-of-order pkt
      PriorityQueue<GBNSegment> sendBuffer = new PriorityQueue<>(sws);
      HashSet<Integer> bsnBufferSet = new HashSet<>();

      if (firstReceivedAck != null && firstReceivedAck.isAck && firstReceivedAck.dataLength > 0) {
        // if handshake ACK is lost, then the first ACK might contain data.
        sendBuffer.add(firstReceivedAck);
        bsnBufferSet.add(firstReceivedAck.byteSequenceNum);
      }

      while (isOpen) {
        // Receive data
        // this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);
        GBNSegment data;
        try {
          data = handlePacket();

          // If a client is sending a cumulative acknowledgment of several packets, the
          // timestamp from the latest received packet which is causing this acknowledgment
          // should be copied into the reply.
          long mostRecentTimestamp = data.timestamp;

          int currBsn = data.byteSequenceNum;
          int firstByteBeyondSws = nextByteExpected + (sws * mtu);
          // Check if received packet is within SWS
          if (currBsn >= firstByteBeyondSws) {
            // Discard out-of-order packets (outside sliding window size)
            System.err.println("Rcv - discard out-of-order packet!!!");
            GBNSegment ackSegment =
                GBNSegment.createAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
            sendPacket(ackSegment, senderIp, senderPort);
            this.numOutOfSeqDiscardPackets++;
            continue; // wait for more packets
          } else if (currBsn < nextByteExpected) { // before sws...?
            // When this condition was part of the discard out-of-order packet
            // and send ACK case above, we were sending a ton of duplicate ACKs which was causing
            // a ton of extra traffic
            // System.err.println("Rcv - discard out-of-order packet!!!");
            GBNSegment ackSegment =
                GBNSegment.createAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
            sendPacket(ackSegment, senderIp, senderPort);
            this.numOutOfSeqDiscardPackets++;
            continue;
          } else {
            // Add packets to buffer if within sliding window size
            if (!bsnBufferSet.contains(currBsn)) {
              bsnBufferSet.add(currBsn);
              sendBuffer.add(data);
              // process send buffer
            } else {
              continue; // wait for more packets
            }

            while (!sendBuffer.isEmpty()) { // restructure this while loop to not be confusing
              GBNSegment minSegment = sendBuffer.peek();

              // check if sendBuffer has next expected packet
              if (minSegment.byteSequenceNum == nextByteExpected) {
                // Terminate Connection
                if (!minSegment.isAck || minSegment.getDataLength() <= 0) {
                  // receive non-data packeton close
                  if (minSegment.isFin) {
                    outStream.close();
                    closeConnection(mostRecentTimestamp);
                    sendBuffer.remove(minSegment);
                    bsnBufferSet.remove(minSegment.byteSequenceNum);
                    isOpen = false;
                  } else {
                    throw new UnexpectedFlagException("Expected ACK and data or FIN!", minSegment);
                  }
                } else {
                  // Reconstruct file and send ACK
                  outStream.write(minSegment.getPayload());

                  nextByteExpected += minSegment.getDataLength();
                  lastByteReceived += minSegment.getDataLength();
                  GBNSegment ackSegment =
                      GBNSegment.createAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
                  sendPacket(ackSegment, senderIp, senderPort);

                  bsnBufferSet.remove(minSegment.byteSequenceNum);
                  sendBuffer.remove(minSegment);
                }
              } else {
                // not next expected packet; send duplicate ACK
                GBNSegment ackSegment =
                    GBNSegment.createAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
                sendPacket(ackSegment, senderIp, senderPort);
                break;
              }
            }
          }
        } catch (SegmentChecksumMismatchException e) {
          System.err.println("bad checksum 1");
          e.printStackTrace();
          this.numChkDiscardPackets++;
          continue;
        } catch (UnexpectedFlagException e) {
          e.printStackTrace();
          continue;
        }
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void closeConnection(long mostRecentTimestamp)
      throws IOException, MaxRetransmitException, UnexpectedFlagException {
    boolean isLastAckReceived = false;
    short currNumRetransmits = 0;
    while (!isLastAckReceived) {

      GBNSegment returnFinAckSegment =
          GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.FINACK);
      sendPacket(returnFinAckSegment, senderIp, senderPort);
      bsn++;

      try {
        this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);
        GBNSegment lastAckSegment;
        try {
          lastAckSegment = handlePacket();
        } catch (SegmentChecksumMismatchException e) {
          e.printStackTrace();
          this.numChkDiscardPackets++;
          continue;
        }

        if (lastAckSegment.isAck) {
          isLastAckReceived = true;
        } else if (lastAckSegment.isFin) {
          // discard Fin retransmission
          bsn--;
          continue;
        } else {
          throw new UnexpectedFlagException();
        }
      } catch (SocketTimeoutException e) {
        System.err.println("Timeout while waiting for last ACK!");
        currNumRetransmits++;
        if (currNumRetransmits >= (MAX_RETRANSMITS + 1)) {
          // exit immediately
          throw new MaxRetransmitException("Max FINACK retransmits!");
        }
        this.numRetransmits++;
        bsn--;
      }
    }
  }

  public void printFinalStatsHeader() {
    System.out.println("TCPEnd Receiver Finished==========");
    this.printFinalStats();
  }

}
