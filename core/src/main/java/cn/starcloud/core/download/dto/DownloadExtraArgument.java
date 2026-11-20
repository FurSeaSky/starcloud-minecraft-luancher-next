package cn.starcloud.core.download.dto;

import java.net.http.HttpRequest;
import java.util.List;

public record DownloadExtraArgument(
    DownloadType type, List<DownloadHead> heads, HttpRequest.BodyPublisher body) {
  public enum DownloadType {
    GET,
    POST,
  }

  public record DownloadHead(String requestHeadKey, String requestHeadValue) {}
}
