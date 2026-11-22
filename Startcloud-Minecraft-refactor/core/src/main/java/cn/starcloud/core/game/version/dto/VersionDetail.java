package cn.starcloud.core.game.version.dto;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Map;

/**
 * One parsed version JSON.
 *
 * <p>Only the fields the launcher needs are modelled; everything else stays available through
 * {@link #rawJson()} so new requirements do not force a DTO change.
 *
 * @param id version id such as {@code 1.21.4}
 * @param mainClass main class the game is started with
 * @param assets asset index id this version was published with
 * @param assetIndex where to download the asset index from
 * @param downloads the game jar and its mappings
 * @param libraries libraries to put on the classpath, already narrowed by the JSON's own rules
 * @param arguments structured game and JVM arguments, absent in pre-1.13 versions
 * @param logging log4j configuration to apply at launch
 * @param inheritsFrom parent version id for versions that only patch another one, {@code null} for
 *     standalone versions
 * @param minecraftArguments legacy pre-1.13 argument string, {@code null} once {@code arguments} is
 *     used
 * @param rawJson the JSON this record was parsed from
 */
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

  /**
   * Where the asset index for this version lives.
   *
   * @param id asset index id, matching the directory name under {@code assets/indexes}
   * @param url download location
   * @param sha1 expected checksum of the index file
   * @param size expected size of the index file
   */
  public record AssetIndex(String id, String url, String sha1, long size) {}

  /**
   * The files Mojang publishes for a version.
   *
   * @param client the game jar
   * @param server the server jar, {@code null} when not published
   * @param clientMappings client obfuscation mappings, {@code null} when not published
   * @param serverMappings server obfuscation mappings, {@code null} when not published
   */
  public record Downloads(
      DownloadInfo client,
      DownloadInfo server,
      DownloadInfo clientMappings,
      DownloadInfo serverMappings) {}

  /**
   * A downloadable file.
   *
   * @param url download location
   * @param sha1 expected checksum
   * @param size expected size in bytes
   * @param path location relative to the root it belongs to, {@code null} when the JSON omits it
   */
  public record DownloadInfo(String url, String sha1, long size, String path) {}

  /**
   * One library entry.
   *
   * @param name Maven coordinates
   * @param downloads the artifact and its native classifiers
   * @param rules conditions deciding whether the library applies to the current platform
   * @param url Maven repository base, {@code null} to use the default
   * @param natives maps an operating system to the classifier holding its native binaries
   */
  public record Library(
      String name,
      LibraryDownloads downloads,
      List<Rule> rules,
      String url,
      Map<String, JsonElement> natives) {

    /**
     * The files of one library.
     *
     * @param artifact the jar put on the classpath
     * @param classifiers extra jars keyed by classifier, used for platform natives
     */
    public record LibraryDownloads(DownloadInfo artifact, Map<String, DownloadInfo> classifiers) {}
  }

  /**
   * Condition deciding whether an entry applies.
   *
   * @param action {@code allow} or {@code disallow}
   * @param os operating system the rule is restricted to, {@code null} for any
   * @param features launcher features the rule depends on, {@code null} when it depends on none
   */
  public record Rule(String action, Os os, Map<String, Boolean> features) {

    /**
     * Operating system a rule is restricted to.
     *
     * @param name operating system name such as {@code windows} or {@code osx}
     * @param version required version pattern, {@code null} for any
     * @param arch required architecture, {@code null} for any
     */
    public record Os(String name, String version, String arch) {}
  }

  /**
   * Structured launch arguments introduced in 1.13.
   *
   * @param game arguments passed to the game, mixing plain strings and conditional objects
   * @param jvm arguments passed to the JVM
   */
  public record Arguments(List<JsonElement> game, List<JsonElement> jvm) {}

  /**
   * Logging configuration of a version.
   *
   * @param client the client side configuration
   */
  public record Logging(LoggingClient client) {

    /**
     * Client side log4j configuration.
     *
     * @param argument JVM argument template receiving the config path
     * @param file the configuration file to download
     * @param type configuration type, currently {@code log4j2-xml}
     */
    public record LoggingClient(String argument, DownloadInfo file, String type) {}
  }
}
