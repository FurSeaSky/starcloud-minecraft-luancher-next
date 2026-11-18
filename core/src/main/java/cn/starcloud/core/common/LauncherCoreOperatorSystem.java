package cn.starcloud.core.common;

import java.util.Locale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum LauncherCoreOperatorSystem {
  WINDOWS,
  MACOS,
  LINUX,
  FREEBSD,
  ANDROID,
  HUAWEI,
  UNKNOWN;

  private static final Logger LOGGER = LogManager.getLogger(LauncherCoreOperatorSystem.class);
  private static final LauncherCoreOperatorSystem CURRENT = detect();

  public static LauncherCoreOperatorSystem getSystem() {
    return CURRENT;
  }

  private static boolean isAndroid() {
    try {
      Class.forName("android.os.Build");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static LauncherCoreOperatorSystem detect() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    LOGGER.debug("os.name = {}", os);

    if (os.contains("win")) return WINDOWS;
    if (os.contains("mac")) return MACOS;
    if (os.contains("freebsd")) return FREEBSD;
    if (os.contains("android") || isAndroid()) return ANDROID;
    if (os.contains("huawei")) return HUAWEI;
    if (os.contains("linux")) return LINUX;
    return UNKNOWN;
  }
}
