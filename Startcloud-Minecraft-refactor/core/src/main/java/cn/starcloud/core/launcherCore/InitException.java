package cn.starcloud.core.launcherCore;

import java.io.IOException;

/**
 * Thrown when the core cannot prepare its on-disk layout.
 *
 * <p>Fatal for the current launch: there is no fallback location, so callers should report the
 * message and stop.
 */
public class InitException extends RuntimeException {

  /**
   * Creates the exception with a message.
   *
   * @param message what could not be initialised
   */
  public InitException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a message and a cause.
   *
   * @param message what could not be initialised
   * @param cause the underlying I/O failure
   */
  public InitException(String message, Throwable cause) {
    super(message, cause);
  }

  public InitException(IOException e) {
    super(e);
  }
}
