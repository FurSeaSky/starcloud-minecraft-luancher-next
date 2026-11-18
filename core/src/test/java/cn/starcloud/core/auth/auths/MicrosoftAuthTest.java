package cn.starcloud.core.auth.auths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.starcloud.core.auth.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MicrosoftAuthTest {

  // ==================================================================
  // 一、测试基础设施：Mock HttpClient + JSON 构造 + 状态等待
  // ==================================================================

  static final class MockHttp {
    private final HttpClient client;
    private final List<HttpRequest> sent = new ArrayList<>();
    private final Deque<Step> fifo = new ArrayDeque<>();
    private final List<Step> rules = new ArrayList<>();

    record Step(Predicate<String> uriMatch, int status, String body) {
      static Step of(String uriContains, int status, String body) {
        return new Step(uri -> uri.contains(uriContains), status, body);
      }
    }

    @SuppressWarnings("unchecked")
    MockHttp() {
      client = mock(HttpClient.class);
      try {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(
                inv -> {
                  HttpRequest req = inv.getArgument(0);
                  sent.add(req);
                  Step step = pick(req.uri().toString());
                  if (step == null) {
                    throw new IllegalStateException("无匹配响应规则: " + req.uri());
                  }
                  HttpResponse<String> resp = mock(HttpResponse.class);
                  when(resp.statusCode()).thenReturn(step.status());
                  when(resp.body()).thenReturn(step.body());
                  when(resp.request()).thenReturn(req);
                  when(resp.uri()).thenReturn(req.uri());
                  return resp;
                });
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    }

    /** FIFO 队首必须匹配当前 URI 才消费，否则走 rules。避免设备码请求误吞 token 轮询规则。 */
    private Step pick(String uri) {
      if (!fifo.isEmpty()) {
        Step head = fifo.peek();
        if (head != null && head.uriMatch().test(uri)) {
          return fifo.poll();
        }
      }
      return rules.stream().filter(r -> r.uriMatch().test(uri)).findFirst().orElse(null);
    }

    MockHttp on(String uriContains, int status, String body) {
      rules.add(Step.of(uriContains, status, body));
      return this;
    }

    MockHttp fifo(String uriContains, int status, String body) {
      fifo.add(Step.of(uriContains, status, body));
      return this;
    }

    HttpClient client() {
      return client;
    }

    List<HttpRequest> sent() {
      return sent;
    }

    HttpRequest findByUri(String uriContains) {
      return sent.stream()
          .filter(r -> r.uri().toString().contains(uriContains))
          .findFirst()
          .orElseThrow(() -> new AssertionError("未发送匹配请求: " + uriContains));
    }
  }

  static final class Json {
    static String deviceCode(String dc, String uc, int interval, int expiresIn) {
      return "{\"device_code\":\""
          + dc
          + "\",\"user_code\":\""
          + uc
          + "\","
          + "\"verification_uri\":\"https://microsoft.com/link\","
          + "\"expires_in\":"
          + expiresIn
          + ",\"interval\":"
          + interval
          + ","
          + "\"message\":\"To sign in, use a web browser to open the page...\"}";
    }

    static String tokenSuccess(String at, String rt, int expiresIn) {
      return "{\"access_token\":\""
          + at
          + "\",\"refresh_token\":\""
          + rt
          + "\","
          + "\"expires_in\":"
          + expiresIn
          + ",\"token_type\":\"Bearer\"}";
    }

    static String xbl(String token, String uhs) {
      return "{\"Token\":\""
          + token
          + "\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\""
          + uhs
          + "\"}]}}";
    }

    static String xsts(String token) {
      return "{\"Token\":\"" + token + "\"}";
    }

    static String mcToken(String at, int expiresIn) {
      return "{\"access_token\":\"" + at + "\",\"expires_in\":" + expiresIn + "}";
    }

    static String profile(String id, String name) {
      return "{\"id\":\"" + id + "\",\"name\":\"" + name + "\"}";
    }

    static String error(String code) {
      return "{\"error\":\"" + code + "\"}";
    }
  }

  static AuthState awaitTerminal(AuthHandle handle, long timeoutSec) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
    while (System.nanoTime() < deadline) {
      AuthState s = handle.state();
      if (s instanceof AuthState.Success || s instanceof AuthState.Failed) return s;
      Thread.sleep(20);
    }
    throw new AssertionError("等待终端态超时，当前: " + handle.state());
  }

  // ==================================================================
  // 二、功能测试（JUnit 5）
  // ==================================================================

  private MockHttp http;

  @BeforeEach
  void setUp() {
    http = new MockHttp();
    MicrosoftAuth.setHttpClientForTest(http.client());
  }

  @AfterEach
  void tearDown() {
    MicrosoftAuth.setHttpClientForTest(null);
  }

  private MicrosoftAuth newAuth(String cid, boolean legacy) {
    MicrosoftAuth a = new MicrosoftAuth();
    a.setClientID(cid);
    a.setLegacy(legacy);
    return a;
  }

  /** 设备码规则同时覆盖 legacy 与 modern 两个端点。 */
  private void stubDeviceCode(int status, String body) {
    http.on("oauth20_connect", status, body); // legacy
    http.on("devicecode", status, body); // modern
  }

  private static void assertFailed(AuthState s, AuthFailureReason reason) {
    assertInstanceOf(AuthState.Failed.class, s, "期望 Failed，实际: " + s);
    assertEquals(reason, ((AuthState.Failed) s).reason());
  }

  private void stubFullSuccessChain(
      String at,
      String rt,
      String xblToken,
      String uhs,
      String xstsToken,
      String mcAt,
      String uuid,
      String name) {
    stubDeviceCode(200, Json.deviceCode("dc", "USER-CODE", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess(at, rt, 3600));
    http.on("user/authenticate", 200, Json.xbl(xblToken, uhs));
    http.on("xsts/authorize", 200, Json.xsts(xstsToken));
    http.on("login_with_xbox", 200, Json.mcToken(mcAt, 86400));
    http.on("minecraft/profile", 200, Json.profile(uuid, name));
  }

  // ---------- 基础 API ----------

  @Test
  @DisplayName("clientId 为空 → INVALID_CLIENT_ID，零 HTTP 请求")
  void emptyClientIdFailsFast() throws Exception {
    MicrosoftAuth auth = newAuth("", true);
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CLIENT_ID);
    assertTrue(http.sent().isEmpty());
  }

  @Test
  @DisplayName("clientId 为 null → INVALID_CLIENT_ID")
  void nullClientIdFailsFast() throws Exception {
    MicrosoftAuth auth = new MicrosoftAuth();
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CLIENT_ID);
    assertTrue(http.sent().isEmpty());
  }

  @Test
  @DisplayName("refresh 未实现 → AuthException")
  void refreshThrows() {
    MicrosoftAuth auth = newAuth("cid", true);
    AuthException ex = assertThrows(AuthException.class, () -> auth.refresh("rt"));
    assertTrue(ex.getMessage().contains("not implemented"));
  }

  @Test
  @DisplayName("logout 是 no-op")
  void logoutNoOp() {
    MicrosoftAuth auth = newAuth("cid", true);
    assertDoesNotThrow(() -> auth.logout("token"));
  }

  // ---------- 设备码阶段 ----------

  @Test
  @DisplayName("设备码非 200 → INVALID_CLIENT_ID")
  void deviceCodeNon200() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(400, Json.error("invalid_client"));
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CLIENT_ID);
  }

  @Test
  @DisplayName("设备码非法 JSON → INVALID_CREDENTIALS")
  void deviceCodeBadJson() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, "not-json{");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("设备码字段缺失 → INVALID_CREDENTIALS")
  void deviceCodeMissingFields() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, "{\"device_code\":\"dc\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("设备码成功 → 首个请求打到设备码端点")
  void deviceCodeEmitsWaiting() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubFullSuccessChain("ms-at", "ms-rt", "xbl", "uhs", "xsts", "mc-at", "uuid", "Alex");
    awaitTerminal(auth.login(), 30);
    assertEquals(MicrosoftAuth.LEGACY_DEVICE_CODE_URL, http.sent().get(0).uri().toString());
  }

  // ---------- 轮询阶段 ----------

  @Test
  @DisplayName("authorization_pending ×2 后成功")
  void pendingThenSuccess() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 400, Json.error("authorization_pending"));
    http.fifo("token", 400, Json.error("authorization_pending"));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 200, Json.xsts("xsts"));
    http.on("login_with_xbox", 200, Json.mcToken("mc-at", 86400));
    http.on("minecraft/profile", 200, Json.profile("uuid", "Steve"));
    assertInstanceOf(AuthState.Success.class, awaitTerminal(auth.login(), 30));
  }

  @Test
  @DisplayName("slow_down 后 interval+5，最终成功（耗时 >=7s）")
  void slowDownThenSuccess() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 400, Json.error("slow_down"));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 200, Json.xsts("xsts"));
    http.on("login_with_xbox", 200, Json.mcToken("mc-at", 86400));
    http.on("minecraft/profile", 200, Json.profile("uuid", "Steve"));

    long t0 = System.nanoTime();
    AuthState s = awaitTerminal(auth.login(), 30);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    assertInstanceOf(AuthState.Success.class, s);
    assertTrue(ms >= 7000, "slow_down 后应 >=7s，实际 " + ms + "ms");
  }

  @Test
  @DisplayName("expired_token → DEVICE_CODE_EXPIRED")
  void expiredToken() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 400, Json.error("expired_token"));
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.DEVICE_CODE_EXPIRED);
  }

  @Test
  @DisplayName("authorization_declined → CANCELLED")
  void declined() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 400, Json.error("authorization_declined"));
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.CANCELLED);
  }

  @Test
  @DisplayName("轮询成功但缺 access_token → INVALID_CREDENTIALS")
  void tokenMissingAccessToken() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, "{\"refresh_token\":\"rt\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  // ---------- XBL / XSTS / MC 阶段 ----------

  @Test
  @DisplayName("XBL 缺 uhs → INVALID_CREDENTIALS")
  void xblMissingUhs() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, "{\"Token\":\"xbl\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("XSTS 非 200 → INVALID_CREDENTIALS")
  void xstsNon200() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 401, "{\"error\":\"XSTS error\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("MC 登录缺 access_token → INVALID_CREDENTIALS")
  void mcLoginMissingToken() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 200, Json.xsts("xsts"));
    http.on("login_with_xbox", 200, "{}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("Profile 缺 name → INVALID_CREDENTIALS")
  void profileMissingName() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 200, Json.xsts("xsts"));
    http.on("login_with_xbox", 200, Json.mcToken("mc-at", 86400));
    http.on("minecraft/profile", 200, "{\"id\":\"uuid\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  // ---------- 完整成功链路 ----------

  @Test
  @DisplayName("完整流程 → Success，Session 字段正确")
  void fullSuccessSession() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubFullSuccessChain(
        "ms-at", "ms-refresh", "xbl-tok", "UHS-1", "xsts-tok", "mc-access", "uuid-abc", "Notch");

    AuthState s = awaitTerminal(auth.login(), 30);
    assertInstanceOf(AuthState.Success.class, s, "实际: " + s);
    AuthSession session = ((AuthState.Success) s).session();
    assertEquals("Notch", session.username());
    assertEquals("uuid-abc", session.uuid());
    assertEquals("mc-access", session.accessToken());
    assertEquals("ms-refresh", session.refreshToken());
    assertNotNull(session.expitesAt());
    assertTrue(session.expitesAt().isAfter(java.time.Instant.now()));

    List<String> uris = http.sent().stream().map(r -> r.uri().toString()).toList();
    assertTrue(uris.get(0).contains("devicecode") || uris.get(0).contains("oauth20_connect"));
    assertTrue(uris.get(uris.size() - 1).contains("minecraft/profile"));
  }

  @Test
  @DisplayName("modern 模式使用 modern 端点")
  void modernModeUsesModernUrls() throws Exception {
    MicrosoftAuth auth = newAuth("cid", false);
    stubFullSuccessChain("a", "r", "x", "u", "y", "m", "id", "n");
    awaitTerminal(auth.login(), 30);
    assertTrue(http.sent().get(0).uri().toString().contains("microsoftonline.com"));
  }

  // ---------- 白盒：单点方法 ----------

  @Test
  @DisplayName("parseError 解析正确 / 非法返回 null")
  void parseErrorDirect() {
    assertEquals(
        "authorization_pending", MicrosoftAuth.parseError(Json.error("authorization_pending")));
    assertNull(MicrosoftAuth.parseError("not-json"));
    assertNull(MicrosoftAuth.parseError("{}"));
  }

  @Test
  @DisplayName("parseTokenSuccess 正常 + 缺 access_token 抛 AbortFlow")
  void parseTokenSuccessDirect() {
    MicrosoftAuth auth = newAuth("cid", true);
    DefaultAuthHandle h = new DefaultAuthHandle();
    TokenSuccessJson ok = auth.parseTokenSuccess(h, Json.tokenSuccess("a", "r", 3600));
    assertEquals("a", ok.accessToken());
    assertEquals("r", ok.refreshToken());

    DefaultAuthHandle h2 = new DefaultAuthHandle();
    assertThrows(AbortFlow.class, () -> auth.parseTokenSuccess(h2, "{\"refresh_token\":\"r\"}"));
    assertInstanceOf(AuthState.Failed.class, h2.state());
  }

  // ==================================================================
  // 三、HITL 测试（真实登录，手动触发）
  // ==================================================================

  @Test
  // @EnabledIfSystemProperty(named = "hitl", matches = "true")
  @DisplayName("HITL：真实设备码登录（需手动在浏览器授权）")
  void hitlRealLogin() throws Exception {
    String clientId = System.getProperty("hitl.clientId");
    if (clientId == null || clientId.isBlank()) {
      clientId = prompt("请输入 Microsoft OAuth clientId: ");
    }
    boolean legacy = !"false".equalsIgnoreCase(System.getProperty("hitl.legacy", "true"));
    String finalClientId = clientId.trim();
    runHitl(finalClientId, legacy, 300);
  }

  public static void main(String[] args) throws Exception {
    String clientId = prompt("请输入 Microsoft OAuth clientId: ");
    String mode = prompt("使用哪种模式？(1=legacy, 2=modern，默认1): ");
    boolean legacy = !"2".equals(mode.trim());
    runHitl(clientId.trim(), legacy, 300);
    System.exit(0);
  }

  static void runHitl(String clientId, boolean legacy, long timeoutSec) throws Exception {
    MicrosoftAuth.setHttpClientForTest(null);
    MicrosoftAuth auth = new MicrosoftAuth();
    auth.setClientID(clientId);
    auth.setLegacy(legacy);

    System.out.println("========== HITL 真实登录 ==========");
    System.out.println("clientId = " + clientId);
    System.out.println("mode     = " + (legacy ? "legacy" : "modern"));
    System.out.println("超时     = " + timeoutSec + "s");
    System.out.println("-----------------------------------");

    long t0 = System.currentTimeMillis();
    AuthHandle handle = auth.login();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec);
    AuthState prev = null;
    AuthState last = null;

    while (System.nanoTime() < deadline) {
      AuthState cur = handle.state();
      if (cur != prev) {
        printState(cur, System.currentTimeMillis() - t0);
        prev = cur;
        last = cur;
      }
      if (cur instanceof AuthState.Success || cur instanceof AuthState.Failed) {
        break;
      }
      Thread.sleep(200);
    }

    if (last == null || !(last instanceof AuthState.Success || last instanceof AuthState.Failed)) {
      System.out.println("!! 超时未完成，当前状态: " + handle.state());
      handle.cancel();
      return;
    }

    System.out.println("===================================");
    if (last instanceof AuthState.Success ok) {
      AuthSession s = ok.session();
      System.out.println("✅ 登录成功");
      System.out.println("  username    = " + s.username());
      System.out.println("  uuid        = " + s.uuid());
      System.out.println("  expitesAt   = " + s.expitesAt());
      System.out.println("  accessToken = " + mask(s.accessToken()));
      System.out.println("  refreshToken= " + mask(s.refreshToken()));
    } else {
      AuthState.Failed f = (AuthState.Failed) last;
      System.out.println("❌ 登录失败");
      System.out.println("  reason  = " + f.reason());
      System.out.println("  message = " + (f.error() == null ? null : f.error()));
      if (f.cause() != null) {
        f.cause().printStackTrace(System.out);
      }
    }
    System.out.println("总耗时: " + (System.currentTimeMillis() - t0) + "ms");
  }

  private static void printState(AuthState s, long elapsedMs) {
    String prefix = "[" + elapsedMs + "ms] ";
    if (s == null) {
      System.out.println(prefix + "状态: null");
    } else if (s instanceof AuthState.WaitingForUser w) {
      System.out.println(prefix + "⏳ WaitingForUser");
      System.out.println("  message         = " + w.instruction());
      System.out.println("  verificationUri = " + w.url());
      System.out.println("  userCode        = " + w.code());
      System.out.println("  >>> 请在浏览器打开上面的 URI，输入 userCode 完成授权 <<<");
    } else if (s instanceof AuthState.Success ok) {
      System.out.println(prefix + "✅ Success");
      System.out.println(
          "  session = " + ok.session().username() + " (" + ok.session().uuid() + ")");
    } else if (s instanceof AuthState.Failed f) {
      System.out.println(prefix + "❌ Failed reason=" + f.reason());
    } else {
      System.out.println(prefix + "状态: " + s);
    }
  }

  private static String mask(String token) {
    if (token == null) return null;
    if (token.length() <= 12) return "***";
    return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
  }

  private static String prompt(String msg) {
    System.out.print(msg);
    System.out.flush();
    try (Scanner sc = new Scanner(System.in)) {
      return sc.hasNextLine() ? sc.nextLine() : "";
    }
  }
}
