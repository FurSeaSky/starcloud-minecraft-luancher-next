package cn.starcloud.core.game.version.exception;

public class VersionException extends RuntimeException {
  public VersionException(String message) {
    super(message);
  }

  public VersionException(String message, Throwable cause) {
    super(message, cause);
  }

  public VersionException() {
    super();
  }
}
