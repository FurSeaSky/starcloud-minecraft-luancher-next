package cn.starcloud.core.download.downloads;

import cn.starcloud.core.download.dto.DownloadExtraArgument;
import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.exception.DownloadException;
import cn.starcloud.core.download.intf.IDownload;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class DownloadStringWait implements IDownload<String> {
  private HttpClient httpClient;

  public DownloadStringWait() {
    this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  public DownloadStringWait(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public void setHttpClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public String download(DownloadRequest req, DownloadExtraArgument extraArgument) throws DownloadException {
    if (extraArgument == null) {
      List<DownloadExtraArgument.DownloadHead> h = List.of();
      extraArgument = new DownloadExtraArgument(DownloadExtraArgument.DownloadType.GET, h, null);
    }

    HttpRequest.Builder httpBuilder = HttpRequest.newBuilder()
        .uri(req.uri());

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
      HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new DownloadException("请求失败，状态码：" + resp.statusCode());
      }
      return resp.body();
    } catch (DownloadException e) {
      throw e;
    } catch (Exception e) {
      throw new DownloadException(e.getMessage(), e);
    }
  }

  @Override
  public String download(DownloadRequest req) throws DownloadException {
    return download(req, null);
  }
}
