package cn.starcloud.core.download.intf;

import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.exception.DownloadException;

/**
 * Entry point of a download strategy.
 *
 * <p>All strategies share the same signature but differ in what they return and in whether they
 * block, so the result type is generic rather than narrowed to a common supertype.
 *
 * @param <T> what a successful run yields: a file result for a single-file download, the response
 *     body for a text download, a batch handle for a scheduler
 */
public interface IDownload<T> {

  /**
   * Runs the download.
   *
   * @param req what to download; batch implementations reject a non-{@code null} request and expect
   *     the tasks to have been pushed beforehand
   * @return strategy-specific result
   * @throws DownloadException when the download fails
   */
  T download(DownloadRequest req) throws DownloadException;
}
