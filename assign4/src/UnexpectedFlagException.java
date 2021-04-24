@SuppressWarnings("serial")
public class UnexpectedFlagException extends Exception {
  public UnexpectedFlagException(String message) {
    super(message);
  }
  
  public UnexpectedFlagException() {
    super();
  }
}
