package cn.starcloud.core.auth;

public class AbortFlow extends RuntimeException {
  public AbortFlow(String message) {
    super(message);
  }

  public AbortFlow(String message, Throwable cause) {
    super(message, cause);
  }

  public AbortFlow() {
    super();
  }
}
