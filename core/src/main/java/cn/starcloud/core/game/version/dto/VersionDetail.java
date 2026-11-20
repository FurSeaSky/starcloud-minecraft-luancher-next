package cn.starcloud.core.game.version.dto;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;

public record VersionDetail(
    String id,
    String mainClass,
    String assets,
    AssetIndex assetIndex,
    Downloads downloads,
    List<Library> libraries,
    Arguments arguments,
    Logging logging,
    String inheritsFrom,
    String minecraftArguments,
    String rawJson) {

  public record AssetIndex(String id, String url, String sha1, long size) {}

  public record Downloads(
      DownloadInfo client,
      DownloadInfo server,
      DownloadInfo clientMappings,
      DownloadInfo serverMappings) {}

  public record DownloadInfo(String url, String sha1, long size, String path) {}

  public record Library(
      String name,
      LibraryDownloads downloads,
      List<Rule> rules,
      String url,
      Map<String, JsonElement> natives) {

    public record LibraryDownloads(DownloadInfo artifact, Map<String, DownloadInfo> classifiers) {}
  }

  public record Rule(String action, Os os, Map<String, Boolean> features) {
    public record Os(String name, String version, String arch) {}
  }

  public record Arguments(List<JsonElement> game, List<JsonElement> jvm) {}

  public record Logging(LoggingClient client) {
    public record LoggingClient(String argument, DownloadInfo file, String type) {}
  }
}
