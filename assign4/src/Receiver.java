import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
    this.effectiveRTT = TCPEnd.INITIAL_TIMEOUT;
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
      printOutput(handshakeSyn, false);
      senderIp = handshakeSynPacket.getAddress();
      senderPort = handshakeSynPacket.getPort();
      nextByteExpected++;

      // Send 2nd Syn+Ack Packet
      GBNSegment handshakeSynAck =
          GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.SYNACK);
      // byte[] hsSynAckBytes = hsSynAck.serialize();
      // DatagramPacket hsSynAckPacket =
      // new DatagramPacket(hsSynAckBytes, hsSynAckBytes.length, senderIp, senderPort);
      sendPacket(handshakeSynAck, senderIp, senderPort);
      bsn++;

      // Receive Ack Packet (3rd leg)
      // byte[] hsAckBytes = new byte[mtu];
      // DatagramPacket hsAckUdp = new DatagramPacket(hsAckBytes, mtu);
      // socket.receive(hsAckUdp);
      // hsAckBytes = hsAckUdp.getData();
      // GBNSegment hsAck = new GBNSegment();
      // hsAck.deserialize(hsAckBytes);

      // Verify checksum first syn packet
      // origChk = hsAck.getChecksum();
      // hsAck.resetChecksum();
      // hsAck.serialize();
      // calcChk = hsAck.getChecksum();
      // if (origChk != calcChk) {
      // System.out.println("Rcvr - ack chk does not match!");
      // }
      // (5.2 "First, if the client's ACK to the server is lost...")
      // TODO: handle case where ACK handshake packet is dropped
      GBNSegment handshakeAck = handlePacket(socket);
      if (!handshakeAck.isAck || handshakeAck.isFin || handshakeAck.isSyn) {
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
      // int lastByteReceived = nextByteExpected; // currently redundant as long as discarding
      // out-of-order pkt
      // int lastByteRead = 0;
      PriorityQueue<GBNSegment> sendBuffer = new PriorityQueue<>(sws);
      HashSet<Integer> bsnBufferSet = new HashSet<>();
      while (isOpen) {
        // Receive data
        GBNSegment data = handlePacket(socket);
        long mostRecentTimestamp = data.timestamp;
        
        // TODO: send duplicate ACK for non-contiguous byte
        int currBsn = data.byteSequenceNum;
        int firstByteBeyondSws = nextByteExpected + (sws * mtu);
        // Check if received packet is within SWS
        if (currBsn >= firstByteBeyondSws || currBsn < nextByteExpected) {
          // Discard out-of-order packets (outside sliding window size)
          System.out.println("Rcv - discard out-of-order packet");
          GBNSegment ackSegment = GBNSegment.createAckSegment(bsn, nextByteExpected);
          sendPacket(ackSegment, senderIp, senderPort);
          continue; // wait for more packets
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
              if (!minSegment.isAck || minSegment.getDataLength() <= 0) { // receive non-data packet
                                                                          // on close
                if (minSegment.isFin) {
                  isOpen = false;

                  // TODO: retransmit ACK
                  GBNSegment returnAckSegment = GBNSegment.createAckSegment(bsn, nextByteExpected);
                  sendPacket(returnAckSegment, senderIp, senderPort);

                  GBNSegment returnFinSegment =
                      GBNSegment.createHandshakeSegment(bsn, nextByteExpected, HandshakeType.FIN);
                  sendPacket(returnFinSegment, senderIp, senderPort);
                  bsn++;

                  GBNSegment lastAckSegment = handlePacket(socket);
                  if (!lastAckSegment.isAck || lastAckSegment.isFin || lastAckSegment.isSyn) {
                    System.out.println("Error: Rcv - unexpected flags!");
                  }
                  socket.close();
                  return;
                } else {
                  System.out.println("Error: Rcv - unexpected flags!");
                }
              }

              // Reconstruct file and send ACK
              // TODO: If a client is sending a cumulative acknowledgment of several packets, the
              // timestamp from the latest received packet which is causing this acknowledgment
              // should be copied into the reply.
              outStream.write(minSegment.getPayload());

              // lastByteReceived += minSegment.getDataLength();
              nextByteExpected += minSegment.getDataLength();
              GBNSegment ackSegment = GBNSegment.createAckSegment(bsn, nextByteExpected);
              sendPacket(ackSegment, senderIp, senderPort);

              bsnBufferSet.remove(minSegment.byteSequenceNum);
              sendBuffer.remove(minSegment);
            } else {
              // not next expected packet; send duplicate ACK
              GBNSegment ackSegment = GBNSegment.createAckSegment(bsn, nextByteExpected);
              sendPacket(ackSegment, senderIp, senderPort);
              break;
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
