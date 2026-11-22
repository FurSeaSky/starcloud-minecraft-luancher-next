package cn.starcloud.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Asserts the properties the data directory must have on the platform under test instead of
 * re-deriving it, so a wrong branch in the implementation actually fails the assertion.
 */
class LauncherCorePathTest {

  @Test
  void returnsAnAbsolutePath() {
    Path path = LauncherCorePath.getSystemAppDataPath();

    assertTrue(path.isAbsolute(), () -> "expected absolute path, got " + path);
  }

  @Test
  void returnsTheSamePathOnEveryCall() {
    assertEquals(LauncherCorePath.getSystemAppDataPath(), LauncherCorePath.getSystemAppDataPath());
  }

  @Test
  void matchesThePlatformConvention() {
    String home = System.getProperty("user.home");
    Path actual = LauncherCorePath.getSystemAppDataPath();

    Path expected =
        switch (LauncherCoreOperatorSystem.getSystem()) {
          case WINDOWS -> Paths.get(System.getenv("APPDATA"));
          case MACOS -> Paths.get(home, "Library", "Application Support");
          case LINUX, FREEBSD -> xdgOrConfig(home);
          case ANDROID, HUAWEI -> Paths.get(home, ".config");
          case UNKNOWN -> Paths.get(home);
        };

    assertEquals(expected, actual);
  }

  private static Path xdgOrConfig(String home) {
    String xdg = System.getenv("XDG_CONFIG_HOME");
    return (xdg != null && !xdg.isBlank()) ? Paths.get(xdg) : Paths.get(home, ".config");
  }
}
