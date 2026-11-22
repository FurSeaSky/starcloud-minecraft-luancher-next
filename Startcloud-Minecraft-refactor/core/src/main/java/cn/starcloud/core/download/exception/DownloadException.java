package cn.starcloud.core.download.exception;

/**
 * A download failed.
 *
 * <p>Unchecked on purpose: transfers run on worker threads that cannot declare exceptions, and
 * callers of the synchronous strategies are expected to decide per call site whether a failed
 * download is worth handling.
 */
public class DownloadException extends RuntimeException {

  /**
   * Creates an exception with only a message.
   *
   * @param message what went wrong, in English
   */
  public DownloadException(String message) {
    super(message);
  }

  /**
   * Creates an exception that keeps the underlying failure.
   *
   * @param message what went wrong, in English
   * @param cause the original failure
   */
  public DownloadException(String message, Throwable cause) {
    super(message, cause);
  }
}
