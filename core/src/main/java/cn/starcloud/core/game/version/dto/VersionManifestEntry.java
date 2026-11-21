package cn.starcloud.core.game.version.dto;

/**
 * One entry of the version manifest.
 *
 * @param id version id such as {@code 1.21.4}
 * @param type release channel, {@code release} or {@code snapshot}
 * @param url where this version's JSON can be downloaded
 * @param time last modification timestamp as published
 * @param releaseTime publication timestamp as published
 * @param sha1 expected checksum of the version JSON
 * @param complianceLevel player safety level the version was rated with
 */
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
