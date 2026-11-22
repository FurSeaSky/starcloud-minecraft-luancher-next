package cn.starcloud.core.game.version.exception;

/**
 * Version metadata could not be fetched, parsed or interpreted.
 *
 * <p>Unchecked: version lookups happen deep in launcher setup, where callers treat any failure the
 * same way.
 */
public class VersionException extends RuntimeException {

  /**
   * Creates an empty exception.
   *
   * <p>Prefer the constructor taking a message: an exception without one tells nothing about what
   * went wrong.
   */
  public VersionException() {
    super();
  }

  /**
   * Creates an exception with only a message.
   *
   * @param message what went wrong, in English
   */
  public VersionException(String message) {
    super(message);
  }

  /**
   * Creates an exception that keeps the underlying failure.
   *
   * @param message what went wrong, in English
   * @param cause the original failure
   */
  public VersionException(String message, Throwable cause) {
    super(message, cause);
  }
}
