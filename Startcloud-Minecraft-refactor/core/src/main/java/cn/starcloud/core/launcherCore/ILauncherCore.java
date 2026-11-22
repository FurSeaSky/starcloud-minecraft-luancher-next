package cn.starcloud.core.launcherCore;

/**
 * Bootstrap contract of the core.
 *
 * <p>Implemented by {@link LauncherCore}; kept as an interface so callers and platform modules can
 * depend on the contract without the singleton.
 */
public interface ILauncherCore {

  /**
   * Prepares the launcher's on-disk layout.
   *
   * @return {@code true} when the launcher is ready to run
   * @throws InitException when preparation fails; the launcher is unusable afterwards
   */
  boolean init() throws InitException;
}
