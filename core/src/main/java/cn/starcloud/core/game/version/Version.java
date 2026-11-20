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

public class Version {
  private static final String MANIFEST_PATH = "/mc/game/version_manifest_v2.json";

  private final DownloadStringSync downloader = new DownloadStringSync();
  private final Gson gson = new Gson();

  private volatile boolean isMirror = true;
  private volatile List<VersionManifestEntry> cachedVersions = null;

  public void setMirror(boolean mirror) {
    if (this.isMirror == mirror) {
      return;
    }
    this.isMirror = mirror;
    this.cachedVersions = null; // 切源清缓存
  }

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
    String base = (isMirror ? LauncherCoreConsts.BMCLSOURCE : LauncherCoreConsts.MOJANGSOURCE).toString();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return URI.create(base + MANIFEST_PATH);
  }
}