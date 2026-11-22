package cn.starcloud.core.download.dto;

import java.net.http.HttpRequest;
import java.util.List;

/**
 * Request shape for the synchronous strategies, which need more than a URL.
 *
 * <p>A {@code null} argument at the call site means a plain {@code GET} without extra headers.
 *
 * @param type HTTP method to use
 * @param heads additional request headers
 * @param body request body, required by {@link DownloadType#POST} and ignored by {@link
 *     DownloadType#GET}
 */
public record DownloadExtraArgument(
    DownloadType type, List<DownloadHead> heads, HttpRequest.BodyPublisher body) {

  /** HTTP method supported by the synchronous strategies. */
  public enum DownloadType {
    /** Fetch the resource. */
    GET,
    /** Submit a body and fetch the response. */
    POST,
  }

  /**
   * One request header.
   *
   * @param requestHeadKey header name
   * @param requestHeadValue header value
   */
  public record DownloadHead(String requestHeadKey, String requestHeadValue) {}
}
