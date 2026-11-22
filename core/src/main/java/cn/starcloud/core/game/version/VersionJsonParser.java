package cn.starcloud.core.game.version;

import cn.starcloud.core.game.version.dto.VersionDetail;
import cn.starcloud.core.game.version.exception.VersionException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Turns a version JSON into a {@link VersionDetail}.
 *
 * <p>Takes the {@link Gson} instance from the caller so callers can share one configured mapper
 * instead of paying for a new one per parse.
 */
public class VersionJsonParser {
  private final Gson gson;

  /**
   * Creates a parser.
   *
   * @param gson mapper used for every parse
   */
  public VersionJsonParser(Gson gson) {
    this.gson = gson;
  }

  /**
   * Parses one version JSON.
   *
   * <p>The raw text is carried into the result because fields the DTO does not model (and the exact
   * formatting) are needed later to launch the game.
   *
   * @param json version JSON as published by the manifest
   * @return the parsed detail, with {@link VersionDetail#rawJson()} holding the original text
   * @throws VersionException when the text is not valid JSON or carries no version id
   */
  public VersionDetail parse(String json) throws VersionException {
    try {
      VersionDetail detail = gson.fromJson(json, VersionDetail.class);
      if (detail == null || detail.id() == null) {
        throw new VersionException("version json missing id");
      }
      return new VersionDetail(
          detail.id(),
          detail.mainClass(),
          detail.assets(),
          detail.assetIndex(),
          detail.downloads(),
          detail.libraries(),
          detail.arguments(),
          detail.logging(),
          detail.inheritsFrom(),
          detail.minecraftArguments(),
          json);
    } catch (JsonSyntaxException e) {
      throw new VersionException("version json parse failed", e);
    }
  }
}
