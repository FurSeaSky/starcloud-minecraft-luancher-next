package cn.starcloud.core.download.downloads;

import cn.starcloud.core.download.dto.DownloadExtraArgument;
import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.dto.DownloadResult;
import cn.starcloud.core.download.exception.DownloadException;
import cn.starcloud.core.download.intf.IDownload;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;

/**
 * Blocking single-file download that writes the payload to disk.
 *
 * <p>Streams straight into the target file rather than buffering in memory, which is what makes it
 * usable for multi-megabyte game jars. Because it writes in place it cannot verify or resume a
 * partial transfer; use {@link DownloadSchedulerAsync} when the payload needs a checksum.
 */
public class DownloadSync implements IDownload<DownloadResult> {
  private HttpClient httpClient;

  /** Creates a downloader with a default client that follows redirects. */
  public DownloadSync() {
    this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  /**
   * Creates a downloader that reuses an existing client.
   *
   * @param httpClient client to send requests with, typically one carrying a shared connection pool
   */
  public DownloadSync(HttpClient httpClient) {
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
   * Downloads {@code req} and reports the resulting file.
   *
   * @param req what to download
   * @param extraArgument method, headers and body to send, {@code null} for a plain {@code GET}
   * @return the written file and its size
   * @throws DownloadException when the status is not 2xx or the transfer fails; the target may be
   *     left partially written
   */
  public DownloadResult download(DownloadRequest req, DownloadExtraArgument extraArgument)
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
      HttpResponse<java.io.InputStream> resp =
          httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new DownloadException("Request failed with status code: " + resp.statusCode());
      }
      try (java.io.InputStream in = resp.body();
          OutputStream out = Files.newOutputStream(req.target())) {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
          out.write(buf, 0, len);
        }
      }
      long size = Files.size(req.target());
      return new DownloadResult(req.target(), size);
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
  public DownloadResult download(DownloadRequest req) throws DownloadException {
    return download(req, null);
  }
}
