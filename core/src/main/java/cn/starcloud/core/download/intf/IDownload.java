package cn.starcloud.core.download.intf;

import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.exception.DownloadException;

public interface IDownload<T> {
  T download(DownloadRequest req) throws DownloadException;
}
