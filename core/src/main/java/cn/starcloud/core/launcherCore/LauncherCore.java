package cn.starcloud.core.launcherCore;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LauncherCore implements ILauncherCore {

  private static final Logger logger = LogManager.getLogger(LauncherCore.class);

  private static final LauncherCore INSTANCE = new LauncherCore();

  private Path currentMinecraftGameDir = LauncherCoreConsts.BASE.resolve(".minecraft");

  private LauncherCore() {}

  public static LauncherCore getInstance() {
    return INSTANCE;
  }

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

  public Path getCurrentMinecraftGameDir() {
    return currentMinecraftGameDir;
  }

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
}
