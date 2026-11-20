package cn.starcloud.core.download.downloads;

import cn.starcloud.core.download.DownloadHandler;
import cn.starcloud.core.download.dto.DownloadRequest;
import cn.starcloud.core.download.dto.DownloadTask;
import cn.starcloud.core.download.enums.TaskState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DownloadSchedulerAsyncTest {

  private HttpServer server;
  private String baseUrl;
  private Path tmpDir;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
    tmpDir = Files.createTempDirectory("download-test");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  // ---------- 1. 基础下载 + SHA-1 ----------

  @Test
  void downloadsSingleFileAndVerifiesSha1() throws Exception {
    byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
    server.createContext("/file", ex -> respond(ex, 200, content));

    Path target = tmpDir.resolve("out.txt");
    String sha1 = sha1Hex(content);

    DownloadTask task = DownloadTask.fromRequest(
        new DownloadRequest(URI.create(baseUrl + "/file"), target, sha1, null));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(2, 5000, 2, 100);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertTrue(handler.isDone());
    assertEquals(1, handler.getTotalTaskCount());
    assertEquals(1, handler.getFinishedTaskCount());
    assertEquals(1, handler.getSuccessTasks().size());
    assertArrayEquals(content, Files.readAllBytes(target));
    assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".part")));
  }

  @Test
  void downloadsMultipleFilesConcurrently() throws Exception {
    byte[] a = "aaa".getBytes(StandardCharsets.UTF_8);
    byte[] b = "bbbb".getBytes(StandardCharsets.UTF_8);
    byte[] c = "ccccc".getBytes(StandardCharsets.UTF_8);
    server.createContext("/a", ex -> respond(ex, 200, a));
    server.createContext("/b", ex -> respond(ex, 200, b));
    server.createContext("/c", ex -> respond(ex, 200, c));

    List<DownloadTask> tasks = List.of(
        DownloadTask.fromRequest(new DownloadRequest(URI.create(baseUrl + "/a"),
            tmpDir.resolve("a.bin"), sha1Hex(a), null)),
        DownloadTask.fromRequest(new DownloadRequest(URI.create(baseUrl + "/b"),
            tmpDir.resolve("b.bin"), sha1Hex(b), null)),
        DownloadTask.fromRequest(new DownloadRequest(URI.create(baseUrl + "/c"),
            tmpDir.resolve("c.bin"), sha1Hex(c), null)));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(2, 5000, 2, 100);
    scheduler.pushTasks(tasks);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertEquals(3, handler.getTotalTaskCount());
    assertEquals(3, handler.getFinishedTaskCount());
    assertEquals(3, handler.getSuccessTasks().size());
    assertArrayEquals(a, Files.readAllBytes(tmpDir.resolve("a.bin")));
    assertArrayEquals(b, Files.readAllBytes(tmpDir.resolve("b.bin")));
    assertArrayEquals(c, Files.readAllBytes(tmpDir.resolve("c.bin")));
  }

  @Test
  void reportsProgress() throws Exception {
    byte[] content = new byte[64 * 1024];
    server.createContext("/big", ex -> respond(ex, 200, content));

    Path target = tmpDir.resolve("big.bin");
    var progressCalls = new java.util.concurrent.atomic.AtomicInteger(0);
    var lastReceived = new java.util.concurrent.atomic.AtomicLong(0);

    DownloadTask task = DownloadTask.fromRequest(new DownloadRequest(
        URI.create(baseUrl + "/big"), target, null,
        (downloaded, total) -> {
          progressCalls.incrementAndGet();
          lastReceived.set(downloaded);
          assertTrue(downloaded > 0);
        }));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(1, 5000, 1, 100);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertTrue(progressCalls.get() > 0, "progress listener should be called");
    assertEquals(content.length, lastReceived.get());
  }

  // ---------- 2. 失败与重试 ----------

  @Test
  void sha1MismatchFails() throws Exception {
    byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
    server.createContext("/file", ex -> respond(ex, 200, content));

    Path target = tmpDir.resolve("bad.bin");
    DownloadTask task = DownloadTask.fromRequest(new DownloadRequest(
        URI.create(baseUrl + "/file"), target, "deadbeef", null));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(1, 5000, 1, 50);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertEquals(1, handler.getFailedTasks().size());
    assertEquals(TaskState.FAILED, task.getState());
    assertTrue(task.getErrorMsg().toLowerCase().contains("sha1"));
    assertFalse(Files.exists(target));
    assertFalse(Files.exists(target.resolveSibling(target.getFileName() + ".part")));
  }

  @Test
  void non2xxFailsWithoutRetry() throws Exception {
    var hits = new java.util.concurrent.atomic.AtomicInteger(0);
    server.createContext("/missing", ex -> {
      hits.incrementAndGet();
      respond(ex, 404, "not found".getBytes(StandardCharsets.UTF_8));
    });

    Path target = tmpDir.resolve("missing.bin");
    DownloadTask task = DownloadTask.fromRequest(
        new DownloadRequest(URI.create(baseUrl + "/missing"), target, null, null));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(1, 5000, 3, 50);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertEquals(1, handler.getFailedTasks().size());
    assertEquals(TaskState.FAILED, task.getState());
    assertEquals(1, hits.get(), "404 should not be retried");
  }

  @Test
  void retriesOnTransientFailure() throws Exception {
    var hits = new java.util.concurrent.atomic.AtomicInteger(0);
    server.createContext("/flaky", ex -> {
      int n = hits.incrementAndGet();
      if (n < 3) {
        respond(ex, 500, "boom".getBytes(StandardCharsets.UTF_8));
      } else {
        respond(ex, 200, "ok".getBytes(StandardCharsets.UTF_8));
      }
    });

    Path target = tmpDir.resolve("flaky.bin");
    DownloadTask task = DownloadTask.fromRequest(
        new DownloadRequest(URI.create(baseUrl + "/flaky"), target, null, null));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(1, 5000, 5, 50);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertEquals(1, handler.getSuccessTasks().size());
    assertEquals(TaskState.SUCCESS, task.getState());
    assertEquals(3, hits.get());
    assertEquals("ok", Files.readString(target));
  }

  // ---------- 3. 取消 ----------

  @Test
  void cancelBeforeStartSkipsTask() throws Exception {
    server.createContext("/file", ex -> respond(ex, 200, "x".getBytes(StandardCharsets.UTF_8)));

    DownloadTask task = DownloadTask.fromRequest(
        new DownloadRequest(URI.create(baseUrl + "/file"), tmpDir.resolve("x.bin"), null, null));
    task.cancel();

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(1, 5000, 1, 50);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    awaitDone(handler);

    assertEquals(1, handler.getCancelledTasks().size());
    assertEquals(TaskState.CANCELLED, task.getState());
    assertFalse(Files.exists(tmpDir.resolve("x.bin")));
  }

  @Test
  void cancelDuringDownloadInterruptsAndKeepsCancelledState() throws Exception {
    // 慢响应：分块写，每块间隔 50ms，让 cancel 有时间插入
    server.createContext("/slow", ex -> {
      ex.sendResponseHeaders(200, 0); // chunked
      OutputStream os = ex.getResponseBody();
      try {
        for (int i = 0; i < 40; i++) {
          os.write(new byte[1024]);
          os.flush();
          Thread.sleep(50);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        os.close();
      }
    });

    DownloadTask task = DownloadTask.fromRequest(
        new DownloadRequest(URI.create(baseUrl + "/slow"), tmpDir.resolve("slow.bin"), null, null));

    DownloadSchedulerAsync scheduler = new DownloadSchedulerAsync(1, 10000, 1, 50);
    scheduler.pushTask(task);
    DownloadHandler handler = scheduler.download();

    // 等任务进入 RUNNING
    waitUntil(() -> task.getState() == TaskState.RUNNING, 2000);

    task.cancel();
    scheduler.shutdownNow();

    awaitDone(handler);

    assertEquals(TaskState.CANCELLED, task.getState());
    assertEquals(1, handler.getCancelledTasks().size());
    assertTrue(handler.getFailedTasks().isEmpty(), "cancelled task must not be marked FAILED");
  }

  // ---------- 辅助 ----------

  private static void respond(HttpExchange ex, int status, byte[] body) throws IOException {
    ex.sendResponseHeaders(status, body.length);
    try (OutputStream os = ex.getResponseBody()) {
      os.write(body);
    }
  }

  private static String sha1Hex(byte[] data) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
  }

  private static void awaitDone(DownloadHandler handler) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (!handler.isDone() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertTrue(handler.isDone(), "handler should be done within 10s");
  }

  private static void waitUntil(java.util.function.BooleanSupplier cond, long timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (!cond.getAsBoolean() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
  }
}