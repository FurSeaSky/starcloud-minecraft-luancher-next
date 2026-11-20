package cn.starcloud.core.launcherCore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the singleton bootstrap: a wrong game directory must be rejected before it is assigned, so
 * the launcher never points at something it cannot use.
 */
class LauncherCoreTest {

  private final LauncherCore core = LauncherCore.getInstance();
  private Path previousGameDir;

  /** The instance is shared, so every test restores the field it started with. */
  @BeforeEach
  void captureGameDir() throws ReflectiveOperationException {
    previousGameDir = readGameDir();
  }

  @AfterEach
  void restoreGameDir() throws ReflectiveOperationException {
    writeGameDir(previousGameDir);
  }

  @Test
  void getInstanceReturnsTheSameSingleInstance() {
    assertSame(core, LauncherCore.getInstance());
  }

  @Test
  void implementsTheBootstrapContract() {
    assertInstanceOf(ILauncherCore.class, core);
  }

  @Test
  void defaultGameDirIsMinecraftUnderBase() {
    assertEquals(LauncherCoreConsts.BASE.resolve(".minecraft"), core.getCurrentMinecraftGameDir());
  }

  @Test
  void setCurrentMinecraftGameDirAcceptsAnExistingDirectory(@TempDir Path dir) throws Exception {
    core.setCurrentMinecraftGameDir(dir);

    assertEquals(dir, core.getCurrentMinecraftGameDir());
  }

  @Test
  void rejectsNull() {
    assertThrows(NullPointerException.class, () -> core.setCurrentMinecraftGameDir(null));
  }

  @Test
  void rejectsMissingDirectory(@TempDir Path dir) {
    Path missing = dir.resolve("absent");

    assertThrows(NoSuchFileException.class, () -> core.setCurrentMinecraftGameDir(missing));
  }

  @Test
  void rejectsRegularFile(@TempDir Path dir) throws Exception {
    Path file = Files.createFile(dir.resolve("game.jar"));

    assertThrows(IllegalArgumentException.class, () -> core.setCurrentMinecraftGameDir(file));
  }

  @Test
  void rejectedPathKeepsThePreviousGameDir(@TempDir Path dir) throws Exception {
    Path accepted = Files.createDirectory(dir.resolve("accepted"));
    core.setCurrentMinecraftGameDir(accepted);

    assertThrows(
        NoSuchFileException.class, () -> core.setCurrentMinecraftGameDir(dir.resolve("absent")));
    assertEquals(accepted, core.getCurrentMinecraftGameDir());
  }

  @Test
  void rejectsUnwritableDirectory(@TempDir Path dir) throws Exception {
    Path readOnly = Files.createDirectory(dir.resolve("read-only"));
    assumeTrue(
        readOnly.getFileSystem().supportedFileAttributeViews().contains("posix"),
        "needs POSIX permissions");
    Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
    assumeTrue(!Files.isWritable(readOnly), "running as root, permissions are not enforced");

    AccessDeniedException denied =
        assertThrows(AccessDeniedException.class, () -> core.setCurrentMinecraftGameDir(readOnly));

    assertEquals("write", denied.getReason());
  }

  @Test
  void baseIsAnAbsolutePath() {
    assertTrue(LauncherCoreConsts.BASE.isAbsolute());
  }

  private Path readGameDir() throws ReflectiveOperationException {
    return (Path) gameDirField().get(core);
  }

  private void writeGameDir(Path dir) throws ReflectiveOperationException {
    gameDirField().set(core, dir);
  }

  private static Field gameDirField() throws NoSuchFieldException {
    Field field = LauncherCore.class.getDeclaredField("currentMinecraftGameDir");
    field.setAccessible(true);
    return field;
  }
}
