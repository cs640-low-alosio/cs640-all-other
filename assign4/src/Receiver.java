import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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

  public GBNSegment openConnection() throws IOException, MaxRetransmitException {
    GBNSegment firstReceivedAck = null;

    this.socket = new DatagramSocket(receiverPort);
    this.socket.setSoTimeout(0);

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
      System.out.println("Rcvr - first syn chk does not match!");
    }
    if (!handshakeSyn.isSyn || handshakeSyn.isAck || handshakeSyn.isFin) {
      System.out.println("Handshake: Rcvr - first segment doesn't have SYN flag!");
    }
    printOutput(handshakeSyn, false);
    senderIp = handshakeSynPacket.getAddress();
    senderPort = handshakeSynPacket.getPort();
    nextByteExpected++;
    numPacketsReceived++;

    boolean isFirstAckReceived = false;
    while (!isFirstAckReceived)
      try {
        // Send 2nd Syn+Ack Packet
        GBNSegment handshakeSynAck =
            GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.SYNACK);
        sendPacket(handshakeSynAck, senderIp, senderPort);
        bsn++;

        // Receive Ack Packet (3rd leg)
        firstReceivedAck = handlePacket();
        if (firstReceivedAck.isAck && !firstReceivedAck.isFin && !firstReceivedAck.isSyn) {
          isFirstAckReceived = true;
        } else {
          System.out.println("Handshake: Rcvr - 3rd segment doesn't have correct flags!");
          this.numRetransmits++;
          if (this.numRetransmits % 17 == 0) {
            // exit immediately
            throw new MaxRetransmitException("Max SYNACK retransmits!");
          }
          bsn--;
          continue;
        }
      } catch (SocketTimeoutException e) {
        this.numRetransmits++;
        if (this.numRetransmits % 17 == 0) {
          // exit immediately
          throw new MaxRetransmitException("Max SYNACK retransmits!");
        }
        System.out.println("Retransmit SYNACK!" + this.numRetransmits);
        bsn--;
        continue;
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

      while (isOpen) {
        // Receive data
        this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);
        GBNSegment data = handlePacket();

        // If a client is sending a cumulative acknowledgment of several packets, the
        // timestamp from the latest received packet which is causing this acknowledgment
        // should be copied into the reply.
        long mostRecentTimestamp = data.timestamp;

        int currBsn = data.byteSequenceNum;
        int firstByteBeyondSws = nextByteExpected + (sws * mtu);
        // Check if received packet is within SWS
        if (currBsn >= firstByteBeyondSws) {
          // Discard out-of-order packets (outside sliding window size)
          System.out.println("Rcv - discard out-of-order packet!!!");
          GBNSegment ackSegment =
              GBNSegment.createAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
          sendPacket(ackSegment, senderIp, senderPort);
          numDiscardPackets++;
          continue; // wait for more packets
        } else if (currBsn < nextByteExpected) { // before sws...?
          // When this condition was part of the discard out-of-order packet
          // and send ACK case above, we were sending a ton of duplicate ACKs which was causing
          // a ton of extra traffic
          // System.out.println("Rcv - discard out-of-order packet!!!");
          // GBNSegment ackSegment =
          // GBNSegment.createAckSegment(bsn, nextByteExpected, mostRecentTimestamp);
          // sendPacket(ackSegment, senderIp, senderPort);
          // numDiscardPackets++;
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

                  closeConnection(mostRecentTimestamp);
                  sendBuffer.remove(minSegment);
                  bsnBufferSet.remove(minSegment.byteSequenceNum);
                  isOpen = false;
                } else {
                  System.out.println("Error: Rcv - unexpected flags!");
                }
                // continue if it's not data and not a FIN
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
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void closeConnection(long mostRecentTimestamp)
      throws IOException, MaxRetransmitException {
    boolean isLastAckReceived = false;
    short currNumRetransmits = 0;
    while (!isLastAckReceived) {

      GBNSegment returnFinAckSegment =
          GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.FINACK);
      sendPacket(returnFinAckSegment, senderIp, senderPort);
      bsn++;

      try {
        this.socket.setSoTimeout(INITIAL_TIMEOUT_MS);
        GBNSegment lastAckSegment = handlePacket();
        if (lastAckSegment.isAck) {
          isLastAckReceived = true;
        } else {
          currNumRetransmits++;
          if (currNumRetransmits >= 17) {
            // exit immediately
            throw new MaxRetransmitException("Max FINACK retransmits!");
          }
          this.numRetransmits++;
          bsn--;
          continue;
        }
      } catch (SocketTimeoutException e) {
        currNumRetransmits++;
        if (currNumRetransmits >= 17) {
          // exit immediately
          throw new MaxRetransmitException("Max FINACK retransmits!");
        }
        System.out.println("retransmit FINACK!" + currNumRetransmits);
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
