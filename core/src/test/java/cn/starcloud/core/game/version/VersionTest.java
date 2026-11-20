package cn.starcloud.core.game.version;

import static org.junit.jupiter.api.Assertions.assertFalse;

import cn.starcloud.core.game.version.dto.VersionManifestEntry;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VersionTest {
  private static final Logger logger = LoggerFactory.getLogger(VersionTest.class);

  @Test
  void getVersionList() {
    Version v = new Version();
    var a = v.getVersionList();
    logger.info("版本列表数量：{}", a.size());
    for (VersionManifestEntry vme : a) {
      logger.info("{}", vme);
    }
    assertFalse(a.isEmpty());
  }
}
