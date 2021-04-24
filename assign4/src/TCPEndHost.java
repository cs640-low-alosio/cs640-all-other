import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.DecimalFormat;

/**
 * @author Garrett
 *
 */
public class TCPEndHost {
  public static final int INITIAL_TIMEOUT_MS = 5000; // initial timeout in ms
  public static final float ALPHA_RTTFACTOR = 0.875F;
  public static final float BETA_DEVFACTOR = 0.75F;
  public static final DecimalFormat threePlaces = new DecimalFormat("0.000");
  public static final short MAX_RETRANSMITS = 16;

  protected int senderSourcePort;
  protected int receiverPort;
  protected InetAddress receiverIp;
  protected String filename;
  protected int mtu;
  protected int sws;
  protected int bsn;
  protected int nextByteExpected;
  protected DatagramSocket socket;
  protected long effRTT;
  protected long effDev;
  protected long timeout;

  // Final stat counters
  protected int numPacketsSent;
  protected int numPacketsReceived;
  protected int lastByteSent;
  protected int lastByteReceived;
  protected int numDiscardPackets;
  protected int numRetransmits;
  protected int numDupAcks;

  public int getSenderSourcePort() {
    return senderSourcePort;
  }

  public void setSenderSourcePort(int senderSourcePort) {
    this.senderSourcePort = senderSourcePort;
  }

  public int getReceiverPort() {
    return receiverPort;
  }

  public void setReceiverPort(int receiverPort) {
    this.receiverPort = receiverPort;
  }

  public InetAddress getReceiverIp() {
    return receiverIp;
  }

  public void setReceiverIp(InetAddress receiverIp) {
    this.receiverIp = receiverIp;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public int getMtu() {
    return mtu;
  }

  public void setMtu(int mtu) {
    this.mtu = mtu;
  }

  public int getSws() {
    return sws;
  }

  public void setSws(int sws) {
    this.sws = sws;
  }

  public int getBsn() {
    return bsn;
  }

  public void setBsn(int bsn) {
    this.bsn = bsn;
  }

  public GBNSegment handlePacket() throws IOException {
    // Receive packet
    byte[] bytes = new byte[mtu + GBNSegment.HEADER_LENGTH_BYTES];
    DatagramPacket packet = new DatagramPacket(bytes, mtu + GBNSegment.HEADER_LENGTH_BYTES);
    this.socket.receive(packet);
    bytes = packet.getData();
    GBNSegment segment = new GBNSegment();
    segment = segment.deserialize(bytes);

    // Verify checksum
    short origChk = segment.getChecksum();
    segment.resetChecksum();
    segment.serialize();
    short calcChk = segment.getChecksum();
    if (origChk != calcChk) {
      System.out.println("Error: Checksum does not match!");
    }
    // TODO: discard packet if checksum doesn't match

    // Recalculate timeout if ACK
    if (segment.isAck && segment.dataLength == 0) {
      if (segment.byteSequenceNum == 0) {
        this.effRTT = (long) (System.nanoTime() - segment.timestamp);
        this.effDev = 0;
        this.timeout = 2 * effRTT;
      } else {
        long sampRTT = (long) (System.nanoTime() - segment.timestamp);
        long sampDev = Math.abs(sampRTT - effRTT);
        this.effRTT = (long) (ALPHA_RTTFACTOR * effRTT + (1 - ALPHA_RTTFACTOR) * sampRTT);
        this.effDev = (long) (BETA_DEVFACTOR * effDev + (1 - BETA_DEVFACTOR) * sampDev);
        this.timeout = this.effRTT + 4 * this.effDev;
      }
    }

    // Final stat counters
    this.numPacketsReceived++;

    printOutput(segment, false);

    return segment;
  }

  public void printOutput(GBNSegment segment, boolean isSender) {
    if (isSender) {
      System.out.print("snd ");
    } else {
      System.out.print("rcv ");
    }
    System.out.print(segment.getTimestamp());
    System.out.print(segment.isSyn ? " S" : " -");
    System.out.print(segment.isAck ? " A" : " -");
    System.out.print(segment.isFin ? " F" : " -");
    System.out.print((segment.getDataLength() > 0) ? " D" : " -");
    System.out.print(" " + segment.byteSequenceNum);
    System.out.print(" " + segment.getDataLength());
    System.out.print(" " + segment.ackNum);
    System.out.println();
  }

  public void sendPacket(GBNSegment segment, InetAddress destIp, int destPort) {
    byte[] segmentBytes = segment.serialize();
    DatagramPacket packet = new DatagramPacket(segmentBytes, segmentBytes.length, destIp, destPort);
    try {
      socket.send(packet);
      this.numPacketsSent++;
    } catch (IOException e) {
      e.printStackTrace();
    }
    printOutput(segment, true);
  }

  public void printFinalStats() {
    System.out.println("  Data Sent (KB): " + threePlaces.format((double) (this.lastByteSent / 1000.0F)));
    System.out.println("  Data Received (KB) : " + threePlaces.format((double) (this.lastByteReceived / 1000.0F)));
    System.out.println("  Packets Sent: " + this.numPacketsSent);
    System.out.println("  Packets Received: " + this.numPacketsReceived);
    System.out.println("  Out-of-Sequence Packets Discarded: " + this.numDiscardPackets);
    System.out.println("  Number of Retransmissions: " + this.numRetransmits);
    System.out.println("  Number of Duplicate Acknowledgements: " + this.numDupAcks);
  }
}
