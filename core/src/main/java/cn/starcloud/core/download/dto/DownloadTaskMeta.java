package cn.starcloud.core.download.dto;

import java.nio.file.Path;

public record DownloadTaskMeta(String url, Path savePath, String expectedSha1) {}
