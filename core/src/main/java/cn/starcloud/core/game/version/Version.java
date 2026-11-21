package cn.starcloud.core.game.version;

import cn.starcloud.core.download.downloads.DownloadStringSync;
import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.game.version.dto.VersionManifest;
import cn.starcloud.core.game.version.dto.VersionManifestEntry;
import cn.starcloud.core.game.version.exception.VersionException;
import cn.starcloud.core.launcherCore.LauncherCoreConsts;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Access to the list of published Minecraft versions.
 *
 * <p>Reads the manifest from the configured source and caches it for the lifetime of the instance:
 * the manifest changes a few times a year, so refetching it per lookup would cost a request for
 * every call. Switching source discards the cache because the two sources can disagree on what
 * exists.
 */
public class Version {
  private static final String MANIFEST_PATH = "/mc/game/version_manifest_v2.json";

  private final DownloadStringSync downloader = new DownloadStringSync();
  private final Gson gson = new Gson();

  private volatile boolean isMirror = true;
  private volatile List<VersionManifestEntry> cachedVersions = null;

  /** Creates an instance that reads from the mirror and holds no cached manifest yet. */
  public Version() {}

  /**
   * Selects which source the manifest is read from.
   *
   * @param mirror {@code true} to use the BMCLAPI mirror, {@code false} for Mojang's official
   *     endpoint
   */
  public void setMirror(boolean mirror) {
    if (this.isMirror == mirror) {
      return;
    }
    this.isMirror = mirror;
    // The cached list came from the previous source and may contain entries the new one lacks.
    this.cachedVersions = null;
  }

  /**
   * Returns the published versions, fetching the manifest on first use.
   *
   * @return the version entries in manifest order
   * @throws VersionException when the manifest cannot be fetched, parsed or is empty
   */
  public List<VersionManifestEntry> getVersionList() {
    List<VersionManifestEntry> local = cachedVersions;
    if (local != null) {
      return local;
    }
    URI versionUri = buildManifestUri();
    String json = downloader.download(new DownloadRequest(versionUri, null, null, null));
    VersionManifest manifest;
    try {
      manifest = gson.fromJson(json, VersionManifest.class);
    } catch (JsonSyntaxException e) {
      throw new VersionException("version manifest json parse failed", e);
    }
    if (manifest == null || manifest.versions() == null) {
      throw new VersionException("version manifest is empty or malformed");
    }
    List<VersionManifestEntry> versions = manifest.versions();
    cachedVersions = versions;
    return versions;
  }

  /**
   * Looks up one version by its identifier.
   *
   * @param id version id such as {@code 1.21.4}
   * @return the matching entry, {@code null} when {@code id} is {@code null} or not published
   */
  public VersionManifestEntry getVersionInfoByID(String id) {
    if (id == null) {
      return null;
    }
    for (VersionManifestEntry entry : getVersionList()) {
      if (id.equals(entry.id())) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Returns every version of one release channel.
   *
   * @param type manifest type such as {@code release} or {@code snapshot}
   * @return matching entries, empty when {@code type} is {@code null} or matches nothing
   */
  public List<VersionManifestEntry> getVersionsByType(String type) {
    List<VersionManifestEntry> result = new ArrayList<>();
    if (type == null) {
      return result;
    }
    for (VersionManifestEntry entry : getVersionList()) {
      if (type.equals(entry.type())) {
        result.add(entry);
      }
    }
    return result;
  }

  private URI buildManifestUri() {
    String base =
        (isMirror ? LauncherCoreConsts.BMCLSOURCE : LauncherCoreConsts.MOJANGSOURCE).toString();
    // The constants end with a slash, the path starts with one; trimming keeps the URI valid.
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return URI.create(base + MANIFEST_PATH);
  }
}
