package cn.starcloud.core.launcherCore;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ILauncherCore}, a process-wide singleton that owns the launcher base directory.
 */
public class LauncherCore implements ILauncherCore {

  private static final Logger logger = LoggerFactory.getLogger(LauncherCore.class);

  private static final LauncherCore INSTANCE = new LauncherCore();

  private Path currentMinecraftGameDir = LauncherCoreConsts.BASE.resolve(".minecraft");

  private boolean isMirror = true;

  private LauncherCore() {}

  public void setMirror(boolean isMirror) {
    this.isMirror = isMirror;
  }


  /**
   * Returns the single instance.
   *
   * @return the process-wide instance
   */
  public static LauncherCore getInstance() {
    return INSTANCE;
  }

  /**
   * Ensures the base directory exists, creating it and its parents when missing.
   *
   * <p>Side effect: creates directories on disk. Safe to call repeatedly.
   *
   * @return always {@code true}
   * @throws InitException when the directory cannot be created; the cause is the original {@link
   *     AccessDeniedException} for permission failures and another {@link IOException} otherwise
   */
  @Override
  public boolean init() throws InitException {
    if (Files.notExists(LauncherCoreConsts.BASE)) {
      logger.warn(
          "Starcloud launcher's base directory doesn't exist, creating: {}",
          LauncherCoreConsts.BASE);
      try {
        Files.createDirectories(LauncherCoreConsts.BASE);
      } catch (AccessDeniedException e) {
        throw new InitException(
            "Create base directory failed: no write permission at parent directory: "
                + LauncherCoreConsts.BASE.getParent(),
            e);
      } catch (IOException e) {
        throw new InitException("Create base directory failed: " + LauncherCoreConsts.BASE, e);
      }
    }
    return true;
  }

  /**
   * Returns the game directory used by the next launch.
   *
   * @return the configured game directory, {@code BASE/.minecraft} until changed
   */
  public Path getCurrentMinecraftGameDir() {
    return currentMinecraftGameDir;
  }

  /**
   * Points the launcher at another game directory after checking it is usable.
   *
   * <p>Validation happens before the field is assigned, so a rejected path leaves the previous
   * value untouched.
   *
   * @param dir the new game directory
   * @throws NullPointerException if {@code dir} is {@code null}
   * @throws NoSuchFileException if {@code dir} does not exist
   * @throws IllegalArgumentException if {@code dir} exists but is not a directory
   * @throws AccessDeniedException if {@code dir} is not readable or not writable
   */
  void setCurrentMinecraftGameDir(Path dir) throws IOException {
    if (dir == null) {
      throw new NullPointerException("game dir must not be null");
    }
    if (Files.notExists(dir)) {
      throw new NoSuchFileException(dir.toString());
    }
    if (!Files.isDirectory(dir)) {
      throw new IllegalArgumentException("game dir is not a directory: " + dir);
    }
    if (!Files.isReadable(dir)) {
      throw new AccessDeniedException(dir.toString(), null, "read");
    }
    if (!Files.isWritable(dir)) {
      throw new AccessDeniedException(dir.toString(), null, "write");
    }
    currentMinecraftGameDir = dir;
  }

  public boolean getIsMirror() {
    return isMirror;
  }
}
