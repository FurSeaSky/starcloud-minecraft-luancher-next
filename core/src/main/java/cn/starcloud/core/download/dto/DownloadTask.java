package cn.starcloud.core.download.dto;

import cn.starcloud.core.download.enums.TaskState;
import cn.starcloud.core.download.intf.ProgressListener;

import java.util.concurrent.atomic.AtomicLong;

public class DownloadTask {
  private final DownloadTaskMeta meta;
  private final ProgressListener progressListener;
  private final AtomicLong receivedBytes = new AtomicLong(0);

  private volatile TaskState state = TaskState.PENDING;
  private volatile long totalBytes = -1;
  private volatile String errorMsg;

  public DownloadTask(DownloadTaskMeta meta, ProgressListener progressListener) {
    this.meta = meta;
    this.progressListener = progressListener;
  }

  public static DownloadTask fromRequest(DownloadRequest req) {
    DownloadTaskMeta meta =
        new DownloadTaskMeta(req.uri().toString(), req.target(), req.expectedSha1());
    return new DownloadTask(meta, req.progress());
  }

  public DownloadTaskMeta meta() {
    return meta;
  }

  public ProgressListener progressListener() {
    return progressListener;
  }

  public TaskState getState() {
    return state;
  }

  public void setState(TaskState state) {
    this.state = state;
  }

  public AtomicLong getReceivedBytes() {
    return receivedBytes;
  }

  public long getTotalBytes() {
    return totalBytes;
  }

  public void setTotalBytes(long totalBytes) {
    this.totalBytes = totalBytes;
  }

  public String getErrorMsg() {
    return errorMsg;
  }

  public void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }

  public void cancel() {
    TaskState s = state;
    if (s == TaskState.SUCCESS || s == TaskState.FAILED || s == TaskState.CANCELLED) {
      return;
    }
    state = TaskState.CANCELLED;
  }
}