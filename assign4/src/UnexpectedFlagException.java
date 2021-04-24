@SuppressWarnings("serial")
public class UnexpectedFlagException extends Exception {
  public UnexpectedFlagException(String message) {
    super(message);
  }

  public UnexpectedFlagException() {
    super();
  }

  public UnexpectedFlagException(String message, GBNSegment segment) {
    super(message + "Got Syn" + segment.isSyn + ", Ack: " + segment.isAck + ", Fin: "
        + segment.isFin + ", dataLength: " + segment.dataLength);
  }
}
