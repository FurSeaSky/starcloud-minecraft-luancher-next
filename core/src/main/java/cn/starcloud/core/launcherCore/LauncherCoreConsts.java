package cn.starcloud.core.launcherCore;

import cn.starcloud.core.common.LauncherCorePath;

import java.net.URI;
import java.nio.file.Path;

/** Fixed locations the launcher works with. */
public final class LauncherCoreConsts {

  /** Constants holder: never instantiated. */
  private LauncherCoreConsts() {}

  /**
   * Root of the launcher's own data, resolved once from the platform data directory.
   *
   * <p>Resolved eagerly at class initialisation: it reflects the environment of the process that
   * first loads this class and does not follow later changes to it.
   */
  public static final Path BASE = LauncherCorePath.getSystemAppDataPath().resolve("StarLauncher");  /**
   * BMCLAPI mirror root for the launcher's metadata and file downloads.
   *
   * <p>Preferred over {@link #MOJANGSOURCE} for clients in mainland China: it mirrors Mojang's
   * version manifests, version JSONs, game jars and asset indexes. Only the host is mirrored; a
   * downloaded version JSON still contains official Mojang URLs, so callers must rewrite those
   * before downloading (see {@link #BMCLLIBRARYSOURCE} for the libraries case).
   *
   * <p>Value: {@code https://bmclapi2.bangbang93.com/}
   */
  public static final URI BMCLSOURCE = URI.create("https://bmclapi2.bangbang93.com/");

  /**
   * Mojang's official metadata root.
   *
   * <p>Used as the fallback when the mirror is unreachable or when official behaviour is required.
   * <b>Note:</b> the current value is missing a URI scheme ({@code launchermeta.mojang.com/}
   * instead of {@code https://launchermeta.mojang.com/}); {@link URI#create(String)} accepts it as
   * a relative URI, but it cannot be used to build absolute download URLs until the scheme is
   * added.
   */
  public static final URI MOJANGSOURCE = URI.create("launchermeta.mojang.com/");

  /**
   * BMCLAPI mirror root for Maven-style library downloads.
   *
   * <p>Libraries are not served under the plain {@link #BMCLSOURCE} path: the official prefix
   * {@code https://libraries.minecraft.net/} maps to {@code https://bmclapi2.bangbang93.com/maven/}.
   * When rewriting a library URL from a version JSON, replace the whole official prefix with this
   * value rather than just swapping the host.
   *
   * <p>Derived from {@link #BMCLSOURCE}; value: {@code https://bmclapi2.bangbang93.com//maven/}.
   * The double slash is harmless for most HTTP clients but worth normalising if you build URLs by
   * string concatenation.
   */
  public static final URI BMCLLIBRARYSOURCE = URI.create(BMCLSOURCE+"/maven/");
}
