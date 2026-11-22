package cn.starcloud.core.common;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves the per-user directory where the launcher and the games it manages keep their data. */
public class LauncherCorePath {

  /** Utility class: keeps no state, so instances would only be noise. */
  private LauncherCorePath() {}

  /**
   * Returns the platform's conventional application data directory for the current user.
   *
   * <p>Reads {@code user.home}, {@code APPDATA} and {@code XDG_CONFIG_HOME} from the environment on
   * every call; no caching, so a caller that changes them sees the change.
   *
   * @return the data directory: {@code %APPDATA%} on Windows, {@code ~/Library/Application Support}
   *     on macOS, {@code $XDG_CONFIG_HOME} or {@code ~/.config} on Linux and FreeBSD, {@code
   *     ~/.config} on Android and Huawei, and {@code ~} on unknown platforms
   */
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
