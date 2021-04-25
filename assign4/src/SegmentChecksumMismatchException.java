@SuppressWarnings("serial")
public class SegmentChecksumMismatchException extends Exception {
  public SegmentChecksumMismatchException(String message) {
    super(message);
  }
  
  public SegmentChecksumMismatchException() {
    super();
  }
}
