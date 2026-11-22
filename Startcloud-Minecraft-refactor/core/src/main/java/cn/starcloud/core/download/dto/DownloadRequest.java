package cn.starcloud.core.download.dto;

import cn.starcloud.core.download.intf.ProgressListener;
import java.net.URI;
import java.nio.file.Path;

/**
 * A single download as requested by the caller.
 *
 * @param uri source to fetch
 * @param target file the payload is written to; parent directories are created on demand and the
 *     transfer goes through a sibling {@code .part} file so a crash never leaves a half written
 *     target behind
 * @param expectedSha1 SHA-1 the payload must match, {@code null} to skip verification
 * @param progress listener receiving transfer progress, {@code null} to skip reporting
 */
public record DownloadRequest(
    URI uri, Path target, String expectedSha1, ProgressListener progress) {}
