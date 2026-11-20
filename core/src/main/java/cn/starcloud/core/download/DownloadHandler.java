package cn.starcloud.core.download;

import cn.starcloud.core.download.dto.DownloadTask;
import cn.starcloud.core.download.enums.TaskState;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadHandler {
  private final List<DownloadTask> taskList;
  private final AtomicBoolean done = new AtomicBoolean(false);
  private final AtomicLong totalByteSize = new AtomicLong(0);
  private final AtomicLong receivedByteSize = new AtomicLong(0);

  public DownloadHandler(List<DownloadTask> taskList) {
    this.taskList = List.copyOf(taskList);
  }

  public int getTotalTaskCount() {
    return taskList.size();
  }

  public int getFinishedTaskCount() {
    return (int) taskList.stream().filter(t -> isFinished(t.getState())).count();
  }

  public long getTotalByteSize() {
    return totalByteSize.get();
  }

  public void addTotalByte(long val) {
    totalByteSize.addAndGet(val);
  }

  public long getReceivedByteSize() {
    return receivedByteSize.get();
  }

  public void addReceivedByte(long val) {
    receivedByteSize.addAndGet(val);
  }

  public boolean isDone() {
    return done.get();
  }

  public void markDone() {
    done.set(true);
  }

  public List<DownloadTask> getTaskList() {
    return taskList;
  }

  public List<DownloadTask> getSuccessTasks() {
    return filterByState(TaskState.SUCCESS);
  }

  public List<DownloadTask> getFailedTasks() {
    return filterByState(TaskState.FAILED);
  }

  public List<DownloadTask> getCancelledTasks() {
    return filterByState(TaskState.CANCELLED);
  }

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