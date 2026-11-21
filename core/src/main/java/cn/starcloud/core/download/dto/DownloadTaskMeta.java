package cn.starcloud.core.download.dto;

import java.nio.file.Path;

/**
 * Immutable description of what a task has to fetch.
 *
 * <p>Kept apart from {@link DownloadRequest} so the scheduler can hold and retry tasks without
 * dragging the per-call progress callback along; the URL is stored as text because tasks are
 * created from already parsed manifests.
 *
 * @param url absolute URL to fetch
 * @param savePath final file location; the payload is staged next to it
 * @param expectedSha1 SHA-1 the payload must match, {@code null} to skip verification
 */
public record DownloadTaskMeta(String url, Path savePath, String expectedSha1) {}
