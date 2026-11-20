package cn.starcloud.core.download.exception;

public class DownloadCancelledException extends RuntimeException {
  public DownloadCancelledException(String message) {
    super(message);
  }

  public DownloadCancelledException(Throwable cause) {
    super(cause);
  }

  public DownloadCancelledException(String message, Throwable cause) {
    super(message, cause);
  }
}
