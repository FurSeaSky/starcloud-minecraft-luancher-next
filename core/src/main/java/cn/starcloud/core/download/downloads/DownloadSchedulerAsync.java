package cn.starcloud.core.download.downloads;

import cn.starcloud.core.download.DownloadHandler;
import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.dto.DownloadTask;
import cn.starcloud.core.download.enums.TaskState;
import cn.starcloud.core.download.exception.DownloadException;
import cn.starcloud.core.download.intf.IDownload;
import cn.starcloud.core.download.intf.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadSchedulerAsync implements IDownload<DownloadHandler> {
  private static final Logger log = LoggerFactory.getLogger(DownloadSchedulerAsync.class);
  private static final int BUF_SIZE = 8192;

  private final BlockingQueue<DownloadTask> pendingQueue = new LinkedBlockingQueue<>();
  private final List<DownloadTask> taskSnapshot = new ArrayList<>();
  private final AtomicInteger pendingCount = new AtomicInteger(0);

  private final int maxConcurrency;
  private final int timeoutMs;
  private final int maxRetry;
  private final long retryDelayMs;
  private final HttpClient httpClient;

  private ExecutorService workerPool;
  private volatile boolean started = false;

  public DownloadSchedulerAsync(int maxConcurrency, int timeoutMs, int maxRetry, long retryDelayMs) {
    this(maxConcurrency, timeoutMs, maxRetry, retryDelayMs,
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build());
  }

  public DownloadSchedulerAsync(int maxConcurrency, int timeoutMs, int maxRetry, long retryDelayMs,
                                HttpClient httpClient) {
    this.maxConcurrency = maxConcurrency;
    this.timeoutMs = timeoutMs;
    this.maxRetry = maxRetry;
    this.retryDelayMs = retryDelayMs;
    this.httpClient = httpClient;
  }

  public void pushTask(DownloadRequest request) {
    pushTask(DownloadTask.fromRequest(request));
  }

  public void pushTask(DownloadTask task) {
    if (started) {
      throw new IllegalStateException("Scheduler already started, cannot push new task");
    }
    taskSnapshot.add(task);
    pendingQueue.offer(task);
  }

  public void pushTasksForRequest(List<DownloadRequest> requests) {
    pushTasks(requests.stream().map(DownloadTask::fromRequest).toList());
  }

  public void pushTasks(List<DownloadTask> tasks) {
    if (started) {
      throw new IllegalStateException("Scheduler already started, cannot push new task");
    }
    taskSnapshot.addAll(tasks);
    tasks.forEach(pendingQueue::offer);
  }

  @Override
  public DownloadHandler download(DownloadRequest req) throws DownloadException {
    if (req != null) {
      throw new DownloadException("Batch scheduler only support req = null, push tasks first");
    }
    if (started) {
      throw new DownloadException("Scheduler already started, cannot call download() twice");
    }
    started = true;
    pendingCount.set(taskSnapshot.size());

    DownloadHandler handler = new DownloadHandler(taskSnapshot);
    workerPool = Executors.newFixedThreadPool(maxConcurrency, new WorkerThreadFactory());
    for (int i = 0; i < maxConcurrency; i++) {
      workerPool.submit(() -> workerLoop(handler));
    }
    log.info("Download scheduler started, maxConcurrency={}, taskCount={}",
        maxConcurrency, taskSnapshot.size());
    return handler;
  }

  public DownloadHandler download() throws DownloadException {
    return download(null);
  }

  private void workerLoop(DownloadHandler handler) {
    try {
      while (true) {
        DownloadTask task = pendingQueue.poll(200, TimeUnit.MILLISECONDS);
        if (task == null) {
          if (pendingCount.get() <= 0) {
            checkAndMarkDone(handler);
            return;
          }
          continue;
        }
        if (task.getState() == TaskState.CANCELLED) {
          finishOne(handler);
          continue;
        }
        task.setState(TaskState.RUNNING);
        try {
          runWithRetry(() -> {
            processSingleTask(task, handler);
            return null;
          }, maxRetry, retryDelayMs);
        } catch (Exception e) {
          fillFailed(task, e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
          // finishOne 不在这里，放到 try/catch 之后，确保重试期间不递减
        }
        finishOne(handler);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.info("Worker interrupted");
    } catch (Exception e) {
      log.error("Worker loop error", e);
    }
  }

  private void processSingleTask(DownloadTask task, DownloadHandler handler) throws Exception {
    URI uri = URI.create(task.meta().url());
    Path targetPath = task.meta().savePath();
    String expectedSha1 = task.meta().expectedSha1();
    ProgressListener listener = task.progressListener();

    Path parent = targetPath.getParent();
    if (parent == null) {
      throw new IOException("target has no parent: " + targetPath);
    }
    Files.createDirectories(parent);

    Path partPath = targetPath.resolveSibling(targetPath.getFileName() + ".part");
    long existLen = Files.exists(partPath) ? Files.size(partPath) : 0L;

    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
        .uri(uri)
        .timeout(Duration.ofMillis(timeoutMs));
    if (existLen > 0) {
      reqBuilder.header("Range", "bytes=" + existLen + "-");
    }
    HttpRequest request = reqBuilder.GET().build();

    HttpResponse<InputStream> resp =
        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
    int code = resp.statusCode();
    if (existLen > 0) {
      if (code != 206) {
        Files.deleteIfExists(partPath);
        throw new IOException("Resume failed, server not support range, status=" + code);
      }
    } else {
      if (code < 200 || code >= 300) {
        throw new IOException("HTTP status " + code);
      }
    }

    long contentLength = resp.headers().firstValueAsLong("content-length").orElse(-1L);
    long totalSize = contentLength >= 0 ? existLen + contentLength : -1L;
    task.setTotalBytes(totalSize);
    if (contentLength > 0) {
      handler.addTotalByte(contentLength);
    }

    MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
    if (existLen > 0) {
      try (FileChannel ch = FileChannel.open(partPath, StandardOpenOption.READ)) {
        ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
        long pos = 0;
        while (pos < existLen) {
          buf.clear();
          int r = ch.read(buf);
          if (r <= 0) {
            break;
          }
          buf.flip();
          sha1.update(buf);
          pos += r;
        }
      }
    }

    try (InputStream is = resp.body();
         FileChannel outCh = FileChannel.open(partPath,
             StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
      byte[] buf = new byte[BUF_SIZE];
      int read;
      while ((read = is.read(buf)) != -1) {
        if (task.getState() == TaskState.CANCELLED) {
          throw new IOException("Task cancelled");
        }
        outCh.write(ByteBuffer.wrap(buf, 0, read));
        sha1.update(buf, 0, read);
        task.getReceivedBytes().addAndGet(read);
        handler.addReceivedByte(read);
        if (listener != null) {
          long recv = existLen + task.getReceivedBytes().get();
          listener.onProgressUpdate(recv, totalSize);
        }
      }
    }

    if (task.getState() == TaskState.CANCELLED) {
      throw new IOException("Task cancelled");
    }

    String actualSha1 = hex(sha1.digest());
    if (expectedSha1 != null && !expectedSha1.equalsIgnoreCase(actualSha1)) {
      Files.deleteIfExists(partPath);
      throw new IOException("sha1 mismatch, expected: " + expectedSha1 + ", actual: " + actualSha1);
    }

    Files.move(partPath, targetPath, StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING);
    task.setState(TaskState.SUCCESS);
    log.info("Download success: {}", targetPath);
  }

  private void fillFailed(DownloadTask task, String msg) {
    if (task.getState() == TaskState.CANCELLED) {
      log.info("Download cancelled: {}", task.meta().savePath());
      return;
    }
    task.setErrorMsg(msg);
    task.setState(TaskState.FAILED);
    log.error("Download failed: {}, msg={}", task.meta().savePath(), msg);
  }

  private void finishOne(DownloadHandler handler) {
    int remaining = pendingCount.decrementAndGet();
    if (remaining <= 0 && pendingQueue.isEmpty()) {
      checkAndMarkDone(handler);
    }
  }

  private void checkAndMarkDone(DownloadHandler handler) {
    if (handler.isDone()) {
      return;
    }
    if (pendingCount.get() <= 0 && pendingQueue.isEmpty()) {
      handler.markDone();
      log.info("All download tasks finished");
    }
  }

  private static String hex(byte[] data) {
    StringBuilder sb = new StringBuilder(data.length * 2);
    for (byte b : data) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private <T> T runWithRetry(RetryableAction<T> action, int maxRetry, long delayMs)
      throws Exception {
    int attempt = 0;
    while (true) {
      try {
        return action.run();
      } catch (Exception e) {
        attempt++;
        if (attempt > maxRetry || !isRetryable(e)) {
          throw e;
        }
        log.warn("Action failed, will retry, attempt={}/{}, delay={}ms", attempt, maxRetry, delayMs, e);
        TimeUnit.MILLISECONDS.sleep(delayMs);
      }
    }
  }

  private boolean isRetryable(Exception e) {
    if (e instanceof InterruptedException) {
      return false;
    }
    String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
    if (msg.contains("sha1 mismatch")) {
      return false;
    }
    if (msg.contains("task cancelled")) {
      return false;
    }
    if (msg.contains("400") || msg.contains("403") || msg.contains("404")) {
      return false;
    }
    return true;
  }

  public void shutdown() {
    if (workerPool != null) {
      workerPool.shutdown();
    }
  }

  public void shutdownNow() {
    if (workerPool != null) {
      workerPool.shutdownNow();
    }
  }

  @FunctionalInterface
  interface RetryableAction<T> {
    T run() throws Exception;
  }

  static class WorkerThreadFactory implements ThreadFactory {
    private final AtomicInteger seq = new AtomicInteger(0);

    @Override
    public Thread newThread(Runnable r) {
      return new Thread(r, "download-worker-" + seq.getAndIncrement());
    }
  }
}