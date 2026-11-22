package cn.starcloud.core.download;

import cn.starcloud.core.download.dto.DownloadTask;
import cn.starcloud.core.download.enums.TaskState;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch progress of one scheduler run.
 *
 * <p>Handed back by {@link cn.starcloud.core.download.downloads.DownloadSchedulerAsync#download()}
 * while the transfers are still running, so every counter is atomic and safely readable from the
 * caller's thread. The task list is a fixed copy: tasks added later are rejected by the scheduler.
 */
public class DownloadHandler {
  private final List<DownloadTask> taskList;
  private final AtomicBoolean done = new AtomicBoolean(false);
  private final AtomicLong totalByteSize = new AtomicLong(0);
  private final AtomicLong receivedByteSize = new AtomicLong(0);

  /**
   * Creates a handler over the tasks of one run.
   *
   * @param taskList tasks included in this run; copied, later changes to the source are not seen
   */
  public DownloadHandler(List<DownloadTask> taskList) {
    this.taskList = List.copyOf(taskList);
  }

  /**
   * Returns how many tasks this run contains.
   *
   * @return task count
   */
  public int getTotalTaskCount() {
    return taskList.size();
  }

  /**
   * Returns how many tasks reached a terminal state.
   *
   * <p>Counts failures and cancellations too: "finished" means "no longer running", not
   * "succeeded".
   *
   * @return number of tasks in {@link TaskState#SUCCESS}, {@link TaskState#FAILED} or {@link
   *     TaskState#CANCELLED}
   */
  public int getFinishedTaskCount() {
    return (int) taskList.stream().filter(t -> isFinished(t.getState())).count();
  }

  /**
   * Returns the total payload size announced by the servers.
   *
   * @return summed content lengths, {@code 0} before the workers read the first response headers
   */
  public long getTotalByteSize() {
    return totalByteSize.get();
  }

  /**
   * Adds to the announced payload size.
   *
   * @param val bytes to add
   */
  public void addTotalByte(long val) {
    totalByteSize.addAndGet(val);
  }

  /**
   * Returns how many bytes were received across all tasks.
   *
   * @return summed received bytes
   */
  public long getReceivedByteSize() {
    return receivedByteSize.get();
  }

  /**
   * Adds to the received byte count.
   *
   * @param val bytes to add
   */
  public void addReceivedByte(long val) {
    receivedByteSize.addAndGet(val);
  }

  /**
   * Tells whether the run is over.
   *
   * @return {@code true} once every task finished and the scheduler marked the run done
   */
  public boolean isDone() {
    return done.get();
  }

  /** Marks the run as done; idempotent. */
  public void markDone() {
    done.set(true);
  }

  /**
   * Returns every task of the run.
   *
   * @return immutable task list
   */
  public List<DownloadTask> getTaskList() {
    return taskList;
  }

  /**
   * Returns the tasks that completed successfully.
   *
   * @return tasks in {@link TaskState#SUCCESS}
   */
  public List<DownloadTask> getSuccessTasks() {
    return filterByState(TaskState.SUCCESS);
  }

  /**
   * Returns the tasks that failed.
   *
   * @return tasks in {@link TaskState#FAILED}
   */
  public List<DownloadTask> getFailedTasks() {
    return filterByState(TaskState.FAILED);
  }

  /**
   * Returns the tasks that were cancelled.
   *
   * @return tasks in {@link TaskState#CANCELLED}
   */
  public List<DownloadTask> getCancelledTasks() {
    return filterByState(TaskState.CANCELLED);
  }

  /**
   * Cancels every task that has not finished yet.
   *
   * <p>Only sets the state: workers stop at their next buffer boundary, so this returns before the
   * transfers have actually stopped.
   */
  public void cancelAll() {
    taskList.forEach(DownloadTask::cancel);
  }

  private List<DownloadTask> filterByState(TaskState state) {
    return taskList.stream().filter(t -> t.getState() == state).toList();
  }

  private static boolean isFinished(TaskState state) {
    return switch (state) {
      case SUCCESS, FAILED, CANCELLED -> true;
      default -> false;
    };
  }
}
