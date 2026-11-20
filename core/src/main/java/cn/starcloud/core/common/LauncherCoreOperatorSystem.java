package cn.starcloud.core.common;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operating system the core is running on.
 *
 * <p>Detected once from {@code os.name} at class initialisation; the result is cached in {@link
 * #getSystem()} because the platform of a process cannot change, and callers on hot paths (path
 * resolution, file layout) should not re-read system properties.
 */
public enum LauncherCoreOperatorSystem {
  /** Microsoft Windows. */
  WINDOWS,
  /** Apple macOS. */
  MACOS,
  /** Linux distributions other than Android and HarmonyOS. */
  LINUX,
  /** FreeBSD. */
  FREEBSD,
  /** Android, including devices that also report {@code linux} in {@code os.name}. */
  ANDROID,
  /** Huawei HarmonyOS, matched before the generic Linux branch. */
  HUAWEI,
  /** None of the above; callers must fall back to a conservative default. */
  UNKNOWN;

  private static final Logger LOGGER = LoggerFactory.getLogger(LauncherCoreOperatorSystem.class);
  private static final LauncherCoreOperatorSystem CURRENT = detect();

  /**
   * Returns the detected platform.
   *
   * @return the platform of the current process
   */
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
