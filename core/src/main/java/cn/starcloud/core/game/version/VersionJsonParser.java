package cn.starcloud.core.game.version;

import cn.starcloud.core.game.version.dto.VersionDetail;
import cn.starcloud.core.game.version.exception.VersionException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class VersionJsonParser {
  private final Gson gson;

  public VersionJsonParser(Gson gson) {
    this.gson = gson;
  }

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