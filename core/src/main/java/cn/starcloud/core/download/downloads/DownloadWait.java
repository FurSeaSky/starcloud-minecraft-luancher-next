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

public class DownloadWait implements IDownload<DownloadResult> {
  private HttpClient httpClient;

  public DownloadWait() {
    this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  public DownloadWait(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public void setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public DownloadResult download(DownloadRequest req, DownloadExtraArgument extraArgument) throws DownloadException {
    if (extraArgument == null) {
      List<DownloadExtraArgument.DownloadHead> h = List.of();
      extraArgument = new DownloadExtraArgument(DownloadExtraArgument.DownloadType.GET, h,null);
    }
    HttpRequest.Builder httpBuilder = HttpRequest.newBuilder()
        .uri(req.uri());

    // 改用switch，抛弃反射
    switch (extraArgument.type()) {
      case GET -> httpBuilder.GET();
      case POST -> httpBuilder.POST(extraArgument.body());
      default -> throw new DownloadException("不支持的请求方式：" + extraArgument.type());
    }

    for (DownloadExtraArgument.DownloadHead head : extraArgument.heads()) {
      httpBuilder.header(head.requestHeadKey(), head.requestHeadValue());
    }

    HttpRequest httpRequest = httpBuilder.build();
    try {
      HttpResponse<java.io.InputStream> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new DownloadException("请求失败，状态码：" + resp.statusCode());
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

  @Override
  public DownloadResult download(DownloadRequest req) throws DownloadException {
    return download(req, null);
  }
}
