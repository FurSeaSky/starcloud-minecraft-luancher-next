package cn.starcloud.core.download.exception;

/**
 * A download was cancelled by the caller.
 *
 * <p>Separate from {@link DownloadException} so that cancellation can be told apart from a real
 * failure on the rare path where it is thrown; the batch scheduler reports cancellation through the
 * task state instead.
 */
public class DownloadCancelledException extends RuntimeException {

  /**
   * Creates an exception with only a message.
   *
   * @param message why the download was cancelled, in English
   */
  public DownloadCancelledException(String message) {
    super(message);
  }

  /**
   * Creates an exception with only a cause.
   *
   * @param cause the cancellation signal that ended the transfer
   */
  public DownloadCancelledException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates an exception that keeps the underlying failure.
   *
   * @param message why the download was cancelled, in English
   * @param cause the cancellation signal that ended the transfer
   */
  public DownloadCancelledException(String message, Throwable cause) {
    super(message, cause);
  }
}
