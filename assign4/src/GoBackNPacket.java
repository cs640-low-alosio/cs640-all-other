import java.nio.ByteBuffer;

/**
 * 
 * @author Garrett
 *
 */
public class GoBackNPacket {
  static final int HEADER_LENGTH_BYTES = 24;
  
  protected int byteSequenceNum;
  protected int ackNum;
  protected long timestamp;
//  protected int totalLength;
  protected int dataLength;
  protected boolean isSyn;
  protected boolean isAck;
  protected boolean isFin;
  protected short checksum;
  protected byte[] payloadData;
  
  public byte[] serialize() {
    int totalLength =  payloadData.length + HEADER_LENGTH_BYTES;
    byte[] allSegmentData = new byte[totalLength];
    
    ByteBuffer bb = ByteBuffer.wrap(allSegmentData);
    bb.putInt(byteSequenceNum);
    bb.putInt(ackNum);
    bb.putLong(timestamp);
    
    // Length and flags word
    int lengthAndFlags = 0b0;
    lengthAndFlags = dataLength << 3;
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
    
    bb.putInt(0x0000);    
    
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
  
  public GoBackNPacket deserialize(byte[] data) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(data);
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
  
  
}