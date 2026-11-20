package cn.starcloud.core.download.dto;

import java.nio.file.Path;

public record DownloadResult(Path path, long size) {}
