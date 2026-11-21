package cn.starcloud.core.download.dto;

import java.nio.file.Path;

/**
 * Outcome of a completed single-file download.
 *
 * @param path the file that was written
 * @param size its size in bytes after the transfer finished
 */
public record DownloadResult(Path path, long size) {}
