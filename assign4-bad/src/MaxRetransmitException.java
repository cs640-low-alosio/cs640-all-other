@SuppressWarnings("serial")
public class MaxRetransmitException extends Exception {
  public MaxRetransmitException(String message) {
    super(message);
  }
  
  public MaxRetransmitException() {
    super();
  }
}
