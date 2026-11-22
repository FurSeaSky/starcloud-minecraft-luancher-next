package cn.starcloud.core.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the platform detection through its private entry point, so every branch is reachable on
 * any host instead of only the one the test happens to run on.
 */
class LauncherCoreOperatorSystemTest {

  private static final String OS_NAME = "os.name";
  private final String savedOsName = System.getProperty(OS_NAME);

  @AfterEach
  void restoreOsName() {
    if (savedOsName == null) {
      System.clearProperty(OS_NAME);
    } else {
      System.setProperty(OS_NAME, savedOsName);
    }
  }

  @Test
  void mapsWindows() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.WINDOWS, detectFor("Windows 11"));
  }

  @Test
  void mapsMacos() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.MACOS, detectFor("Mac OS X"));
  }

  @Test
  void mapsFreeBsdBeforeLinux() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.FREEBSD, detectFor("FreeBSD"));
  }

  @Test
  void mapsAndroid() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.ANDROID, detectFor("Linux Android 13"));
  }

  @Test
  void mapsHuaweiBeforeLinux() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.HUAWEI, detectFor("Huawei HarmonyOS"));
  }

  @Test
  void mapsLinux() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.LINUX, detectFor("Linux"));
  }

  @Test
  void fallsBackToUnknown() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.UNKNOWN, detectFor("Solaris"));
  }

  @Test
  void ignoresCase() throws Exception {
    assertEquals(LauncherCoreOperatorSystem.WINDOWS, detectFor("WINDOWS SERVER"));
  }

  @Test
  void treatsMissingPropertyAsUnknown() throws Exception {
    System.clearProperty(OS_NAME);

    assertEquals(LauncherCoreOperatorSystem.UNKNOWN, invokeDetect());
  }

  @Test
  void currentSystemIsDetectedOnce() {
    assertEquals(LauncherCoreOperatorSystem.getSystem(), LauncherCoreOperatorSystem.getSystem());
  }

  private static LauncherCoreOperatorSystem detectFor(String osName) throws Exception {
    System.setProperty(OS_NAME, osName);
    return invokeDetect();
  }

  private static LauncherCoreOperatorSystem invokeDetect() throws Exception {
    Method detect = LauncherCoreOperatorSystem.class.getDeclaredMethod("detect");
    detect.setAccessible(true);
    return (LauncherCoreOperatorSystem) detect.invoke(null);
  }
}
