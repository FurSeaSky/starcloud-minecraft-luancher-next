package cn.starcloud.core.game.version.dto;

import java.util.List;

public record VersionManifest(Latest latest, List<VersionManifestEntry> versions) {
  public record Latest(String release, String snapshot) {}
}