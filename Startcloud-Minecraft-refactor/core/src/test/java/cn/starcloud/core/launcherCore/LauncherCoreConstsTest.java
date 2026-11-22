package cn.starcloud.core.launcherCore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.starcloud.core.common.LauncherCorePath;
import org.junit.jupiter.api.Test;

/**
 * The base directory decides where every launcher file lands, so it must stay under the platform
 * data directory instead of being hard-coded.
 */
class LauncherCoreConstsTest {

  @Test
  void baseIsTheLauncherFolderInsideThePlatformDataDirectory() {
    assertEquals(
        LauncherCorePath.getSystemAppDataPath().resolve("StarLauncher"), LauncherCoreConsts.BASE);
  }
}
