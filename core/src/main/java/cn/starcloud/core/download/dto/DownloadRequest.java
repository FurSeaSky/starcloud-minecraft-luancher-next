package cn.starcloud.core.download.dto;

import cn.starcloud.core.download.intf.ProgressListener;

import java.net.URI;
import java.nio.file.Path;

public record DownloadRequest(
    URI uri,
    Path target,
    String expectedSha1,
    ProgressListener progress
) {}