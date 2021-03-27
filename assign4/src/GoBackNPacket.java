/**
 * 
 * @author Garrett
 *
 */
public class GoBackNPacket {
  protected int byteSequenceNum;
  protected int ackNum;
  protected long timestamp;
  protected int length;
  protected boolean isSyn;
  protected boolean isAck;
  protected boolean isFin;
  protected short checksum;
  protected byte[] data;
  
  public byte[] serialize() {
    return null;
  }
  
  public GoBackNPacket deserialize(byte[] data) {
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
  public int getLength() {
    return length;
  }
  public void setLength(int length) {
    this.length = length;
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