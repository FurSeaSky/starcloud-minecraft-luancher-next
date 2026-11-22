package cn.starcloud.core.download.dto;

import cn.starcloud.core.download.enums.TaskState;
import cn.starcloud.core.download.intf.ProgressListener;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One queued download and its live progress.
 *
 * <p>Mutated by the worker that owns it while callers read it from other threads, so the mutable
 * fields are {@code volatile} and the byte counter is atomic. Cancellation is a state flag rather
 * than an interrupt: the worker checks it between buffers and stops writing at the next safe point.
 */
public class DownloadTask {
  private final DownloadTaskMeta meta;
  private final ProgressListener progressListener;
  private final AtomicLong receivedBytes = new AtomicLong(0);

  private volatile TaskState state = TaskState.PENDING;
  private volatile long totalBytes = -1;
  private volatile String errorMsg;

  /**
   * Creates a task in {@link TaskState#PENDING}.
   *
   * @param meta what to fetch and where to put it
   * @param progressListener listener receiving progress, may be {@code null}
   */
  public DownloadTask(DownloadTaskMeta meta, ProgressListener progressListener) {
    this.meta = meta;
    this.progressListener = progressListener;
  }

  /**
   * Creates a task from a caller request.
   *
   * @param req the request to convert
   * @return pending task carrying the request's URL, target, checksum and listener
   */
  public static DownloadTask fromRequest(DownloadRequest req) {
    DownloadTaskMeta meta =
        new DownloadTaskMeta(req.uri().toString(), req.target(), req.expectedSha1());
    return new DownloadTask(meta, req.progress());
  }

  /**
   * Returns the immutable description of this task.
   *
   * @return the task metadata
   */
  public DownloadTaskMeta meta() {
    return meta;
  }

  /**
   * Returns the listener receiving progress for this task.
   *
   * @return the listener, {@code null} when the caller did not ask for progress
   */
  public ProgressListener progressListener() {
    return progressListener;
  }

  /**
   * Returns the current state.
   *
   * @return the state the worker last set
   */
  public TaskState getState() {
    return state;
  }

  /**
   * Sets the current state.
   *
   * @param state the new state
   */
  public void setState(TaskState state) {
    this.state = state;
  }

  /**
   * Returns the live counter of bytes received in this attempt.
   *
   * <p>Atomic because the worker increments it while progress callbacks read it.
   *
   * @return the counter itself, not a snapshot
   */
  public AtomicLong getReceivedBytes() {
    return receivedBytes;
  }

  /**
   * Returns the expected payload size.
   *
   * @return total bytes including a resumed prefix, {@code -1} until the server reports a length
   */
  public long getTotalBytes() {
    return totalBytes;
  }

  /**
   * Records the expected payload size.
   *
   * @param totalBytes total bytes, {@code -1} when unknown
   */
  public void setTotalBytes(long totalBytes) {
    this.totalBytes = totalBytes;
  }

  /**
   * Returns why the task failed.
   *
   * @return the failure message, {@code null} unless the task is in {@link TaskState#FAILED}
   */
  public String getErrorMsg() {
    return errorMsg;
  }

  /**
   * Records why the task failed.
   *
   * @param errorMsg the failure message, in English
   */
  public void setErrorMsg(String errorMsg) {
    this.errorMsg = errorMsg;
  }

  /**
   * Requests cancellation.
   *
   * <p>Idempotent and safe from any thread. A task that already finished is left untouched: the
   * terminal state it reached first wins, so a late cancel cannot resurrect a completed transfer.
   */
  public void cancel() {
    TaskState s = state;
    if (s == TaskState.SUCCESS || s == TaskState.FAILED || s == TaskState.CANCELLED) {
      return;
    }
    state = TaskState.CANCELLED;
  }
}
