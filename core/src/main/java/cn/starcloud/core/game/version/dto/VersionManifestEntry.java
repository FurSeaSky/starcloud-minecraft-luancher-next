package cn.starcloud.core.game.version.dto;

public record VersionManifestEntry(
    String id,
    String type,
    String url,
    String time,
    String releaseTime,
    String sha1,
    int complianceLevel) {
  @Override
  public String toString() {
    return "id:%s,type:%s,url:%s,time:%s,releaaseTime:%s,sha1:%s,complianceLevel:%d"
        .formatted(id, type, url, time, releaseTime, sha1, complianceLevel);
  }
}
