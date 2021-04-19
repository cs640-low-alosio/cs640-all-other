import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * @author Garrett
 *
 */
public class TCPEndHost {  
  protected int senderSourcePort;
  protected int receiverPort;
  protected InetAddress receiverIp;
  protected String filename;
  protected int mtu;
  protected int sws;
  protected int bsn;
  protected int nextByteExpected;
  protected DatagramSocket socket;
  protected long effectiveRTT;
  protected long effectiveDeviation;

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

  public GBNSegment handlePacket(DatagramSocket rcvSocket) {
    // Receive packet
    byte[] bytes = new byte[mtu + GBNSegment.HEADER_LENGTH_BYTES];
    DatagramPacket packet = new DatagramPacket(bytes, mtu + GBNSegment.HEADER_LENGTH_BYTES);
    try {
      rcvSocket.receive(packet);
    } catch (IOException e) {
      e.printStackTrace();
    }
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
    } catch (IOException e) {
      e.printStackTrace();
    }
    printOutput(segment, true);
  }
}
