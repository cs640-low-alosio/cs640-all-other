import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 
 * @author Garrett
 *
 */
public class GBNSegment {
  static final int HEADER_LENGTH_BYTES = 24;

  protected int byteSequenceNum;
  protected int ackNum;
  protected long timestamp;
  // protected int totalLength;
  protected boolean isSyn;
  protected boolean isFin;
  protected boolean isAck;
  protected short checksum;
  protected byte[] payloadData;
  protected int dataLength;

  public GBNSegment() {
    this.byteSequenceNum = 0;
    this.ackNum = 0;
    this.timestamp = System.nanoTime();
    this.isSyn = false;
    this.isFin = false;
    this.isAck = false;
    this.checksum = 0;
    this.payloadData = new byte[0];
    this.dataLength = 0;
  }

  public GBNSegment(int bsNum, int ackNum, boolean isSyn, boolean isFin, boolean isAck,
      byte[] payloadData, int dataLength) {
    this.byteSequenceNum = bsNum;
    this.ackNum = ackNum;
    this.timestamp = System.nanoTime();
    this.isSyn = isSyn;
    this.isFin = isFin;
    this.isAck = isAck;
    this.checksum = 0;
    this.payloadData = payloadData;
    this.dataLength = dataLength;
  }
  
  /**
   * Static factory methods
   */
  /**
   * 
   * @param bsNum
   * @param payloadData
   * @return
   */
  public static GBNSegment createDataSegment(int bsNum, int ackNum, byte[] payloadData) {
    return new GBNSegment(bsNum, ackNum, true, false, false, payloadData, payloadData.length);
  }
  
  public static GBNSegment createHandshakeSegment(int bsNum, HandshakeType type) {
    if (type == HandshakeType.SYN) {
      return new GBNSegment(bsNum, 0, true, false, false, new byte[0], 0); 
    } else if (type == HandshakeType.SYNACK) {
      return new GBNSegment(bsNum, 0, true, false, true, new byte[0], 0);
    } else if (type == HandshakeType.ACK) {
      return new GBNSegment(bsNum, 0, true, false, true, new byte[0], 0);      
    } else {
      return null;
    }
  }

  public byte[] serialize() {
    int totalLength;
    if (payloadData == null) {
      totalLength = HEADER_LENGTH_BYTES;
    } else {
      totalLength = payloadData.length + HEADER_LENGTH_BYTES;
    }
    byte[] allSegmentData = new byte[totalLength];

    ByteBuffer bb = ByteBuffer.wrap(allSegmentData);
    bb.putInt(byteSequenceNum);
    bb.putInt(ackNum);
    bb.putLong(timestamp);

    // Length and flags word
    int lengthAndFlags = 0b0;
    lengthAndFlags = dataLength << 3; // add three bits for flags
    if (isSyn) {
      lengthAndFlags += (0b1 << 2);
    }
    if (isFin) {
      lengthAndFlags += (0b1 << 1);
    }
    if (isAck) {
      lengthAndFlags += (0b1 << 0);
    }
    bb.putInt(lengthAndFlags);

    bb.putInt(0x0000); // don't calculate checksum yet

    if (dataLength != 0) {
      bb.put(payloadData);
    }

    // Calculate checksum
    bb.rewind();
    int tempSum = 0;
    for (int i = 0; i < allSegmentData.length / 2; i++) {
      tempSum += bb.getShort();
    }
    if (allSegmentData.length % 2 == 1) { // there is an extra byte at the end
      tempSum += (bb.get() & 0xff) << 8;
    }
    // Handle carry-over
    while (tempSum > 0xffff) {
      int carryoverBits = tempSum >> 16;
      int lastSixteenBits = tempSum - ((tempSum >> 16) << 16);
      tempSum = lastSixteenBits + carryoverBits;
    }
    this.checksum = (short) (~tempSum & 0xffff);

    bb.putShort(22, this.checksum);

    return allSegmentData;
  }

  public GBNSegment deserialize(byte[] data) {
    ByteBuffer bb = ByteBuffer.wrap(data);

    this.byteSequenceNum = bb.getInt();
    this.ackNum = bb.getInt();
    this.timestamp = bb.getLong();
    // Length and Flags
    int lengthAndFlags = bb.getInt();
    this.dataLength = lengthAndFlags >> 3; // remove three bits for flags
    this.isSyn = false;
    this.isAck = false;
    this.isFin = false;
    if (((lengthAndFlags >> 2) & 0b1) == 1) {
      this.isSyn = true;
    }
    if (((lengthAndFlags >> 1) & 0b1) == 1) {
      this.isFin = true;
    }
    if ((lengthAndFlags & 0b1) == 1) {
      this.isAck = true;
    }
    bb.getShort(); // these should all be 0
    this.checksum = bb.getShort();

    // See src/net.floodlightcontroller.packet/Data.java
    this.payloadData = Arrays.copyOfRange(data, bb.position(), bb.limit() - bb.position());

    return null;
  }

  public int getByteSequenceNum() {
    return byteSequenceNum;
  }

  public void setByteSequenceNum(int byteSequenceNum) {
    this.byteSequenceNum = byteSequenceNum;
  }

  public int getAckNum() {
    return ackNum;
  }

  public void setAckNum(int ackNum) {
    this.ackNum = ackNum;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public int getDataLength() {
    return dataLength;
  }

  public void setLength(int dataLength) {
    this.dataLength = dataLength;
  }

  public boolean isSyn() {
    return isSyn;
  }

  public void setSyn(boolean isSyn) {
    this.isSyn = isSyn;
  }

  public boolean isAck() {
    return isAck;
  }

  public void setAck(boolean isAck) {
    this.isAck = isAck;
  }

  public boolean isFin() {
    return isFin;
  }

  public void setFin(boolean isFin) {
    this.isFin = isFin;
  }

  public short getChecksum() {
    return checksum;
  }

  public void setChecksum(short checksum) {
    this.checksum = checksum;
  }

  public void resetChecksum() {
    this.checksum = 0;
  }


}