package cn.starcloud.core.game.version.dto;

import java.util.List;

/**
 * The version manifest: every published version plus the current release and snapshot.
 *
 * @param latest the newest release and snapshot ids
 * @param versions every published version, newest first
 */
public record VersionManifest(Latest latest, List<VersionManifestEntry> versions) {

  /**
   * The newest version of each channel.
   *
   * @param release newest full release id
   * @param snapshot newest snapshot id
   */
  public record Latest(String release, String snapshot) {}
}
