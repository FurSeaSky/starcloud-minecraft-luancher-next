package cn.starcloud.core.download.enums;

/**
 * Lifecycle of a single download task.
 *
 * <p>{@link #SUCCESS}, {@link #FAILED} and {@link #CANCELLED} are terminal: once a task reaches one
 * of them it is never moved again, which is what lets the scheduler count completions and report
 * results after the workers have drained.
 */
public enum TaskState {
  /** Queued, waiting for a worker thread. */
  PENDING,
  /** Worker owns the task and is transferring bytes into the {@code .part} file. */
  RUNNING,
  /** Payload written and, when the request carried one, the SHA-1 checksum matched. */
  SUCCESS,
  /** Network, parsing, checksum or timeout failure. */
  FAILED,
  /** Cancelled by the caller before it reached a terminal state. */
  CANCELLED
}
