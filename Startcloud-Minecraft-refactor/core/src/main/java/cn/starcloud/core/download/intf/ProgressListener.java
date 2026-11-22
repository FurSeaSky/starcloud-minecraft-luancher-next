package cn.starcloud.core.download.intf;

/**
 * Receives transfer progress of a single download.
 *
 * <p>Called from the thread that performs the transfer, potentially once per buffer.
 * Implementations must return quickly and must not block the worker they run on.
 */
public interface ProgressListener {

  /**
   * Reports how much of the payload has been transferred so far.
   *
   * @param progress bytes written to disk so far, including the part resumed from an earlier
   *     attempt
   * @param total expected total size, {@code -1} when the server did not send a content length
   */
  void onProgressUpdate(long progress, long total);
}
