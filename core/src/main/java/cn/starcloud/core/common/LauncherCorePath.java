package cn.starcloud.core.common;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LauncherCorePath {

  public static Path getSystemAppDataPath() {
    String home = System.getProperty("user.home");
    return switch (LauncherCoreOperatorSystem.getSystem()) {
      case WINDOWS -> Paths.get(System.getenv("APPDATA"));
      case MACOS -> Paths.get(home, "Library", "Application Support");
      case LINUX, FREEBSD -> Paths.get(resolveXdgConfigHome(home));
      case ANDROID, HUAWEI -> Paths.get(home, ".config");
      case UNKNOWN -> Paths.get(home);
    };
  }

  private static String resolveXdgConfigHome(String home) {
    String xdg = System.getenv("XDG_CONFIG_HOME");
    return (xdg != null && !xdg.isBlank()) ? xdg : home + "/.config";
  }
}
