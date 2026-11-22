package cn.starcloud.core.download.downloads;

import cn.starcloud.core.download.dto.DownloadExtraArgument;
import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.exception.DownloadException;
import cn.starcloud.core.download.intf.IDownload;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Blocking download that returns the response as text instead of writing a file.
 *
 * <p>Used for metadata such as version manifests and version JSONs, where the payload is small and
 * the caller wants to parse it in memory. Independent of {@link DownloadSync} because the target
 * path on a {@link DownloadRequest} is meaningless here.
 */
public class DownloadStringSync implements IDownload<String> {
  private HttpClient httpClient;

  /** Creates a downloader with a default client that follows redirects. */
  public DownloadStringSync() {
    this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  /**
   * Creates a downloader that reuses an existing client.
   *
   * @param httpClient client to send requests with, typically one carrying a shared connection pool
   */
  public DownloadStringSync(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Replaces the client used for subsequent downloads.
   *
   * @param httpClient the new client
   */
  public void setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /**
   * Downloads {@code req} and returns the decoded body.
   *
   * @param req what to download; only {@link DownloadRequest#uri()} is used
   * @param extraArgument method, headers and body to send, {@code null} for a plain {@code GET}
   * @return the response body as text
   * @throws DownloadException when the status is not 2xx or the transfer fails
   */
  public String download(DownloadRequest req, DownloadExtraArgument extraArgument)
      throws DownloadException {
    if (extraArgument == null) {
      List<DownloadExtraArgument.DownloadHead> h = List.of();
      extraArgument = new DownloadExtraArgument(DownloadExtraArgument.DownloadType.GET, h, null);
    }

    HttpRequest.Builder httpBuilder = HttpRequest.newBuilder().uri(req.uri());

    switch (extraArgument.type()) {
      case GET -> httpBuilder.GET();
      case POST -> httpBuilder.POST(extraArgument.body());
      default -> throw new DownloadException("Unsupported request method: " + extraArgument.type());
    }

    for (DownloadExtraArgument.DownloadHead head : extraArgument.heads()) {
      httpBuilder.header(head.requestHeadKey(), head.requestHeadValue());
    }

    HttpRequest httpRequest = httpBuilder.build();
    try {
      HttpResponse<String> resp =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new DownloadException("Request failed with status code: " + resp.statusCode());
      }
      return resp.body();
    } catch (DownloadException e) {
      throw e;
    } catch (Exception e) {
      throw new DownloadException(e.getMessage(), e);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Equivalent to {@link #download(DownloadRequest, DownloadExtraArgument)} with a plain {@code
   * GET}.
   */
  @Override
  public String download(DownloadRequest req) throws DownloadException {
    return download(req, null);
  }
}
