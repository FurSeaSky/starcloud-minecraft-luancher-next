package cn.starcloud.core.auth.auths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.dto.TokenSuccessJson;
import cn.starcloud.core.auth.enums.AuthFailureReason;
import cn.starcloud.core.auth.exception.AbortFlow;
import cn.starcloud.core.auth.exception.AuthException;
import cn.starcloud.core.auth.itfes.AuthHandle;
import cn.starcloud.core.auth.itfes.AuthState;
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

/**
 * Drives the whole device code chain against a stubbed client, so every failure branch is reachable
 * without a Microsoft account or network access.
 */
class MicrosoftAuthTest {

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
                    throw new IllegalStateException("No matching response rule for: " + req.uri());
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

    /**
     * Only consumes the FIFO head when it matches the URI; otherwise the device code request would
     * swallow the stub queued for the first token poll.
     */
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
          .orElseThrow(
              () -> new AssertionError("No matching request was sent for: " + uriContains));
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
    throw new AssertionError("Timed out waiting for a terminal state, current: " + handle.state());
  }

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

  /** Registers the same stub for both endpoint families. */
  private void stubDeviceCode(int status, String body) {
    http.on("oauth20_connect", status, body);
    http.on("devicecode", status, body);
  }

  private static void assertFailed(AuthState s, AuthFailureReason reason) {
    assertInstanceOf(AuthState.Failed.class, s, "expected Failed but was: " + s);
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

  @Test
  @DisplayName("blank clientId -> INVALID_CLIENT_ID, zero HTTP requests")
  void emptyClientIdFailsFast() throws Exception {
    MicrosoftAuth auth = newAuth("", true);
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CLIENT_ID);
    assertTrue(http.sent().isEmpty());
  }

  @Test
  @DisplayName("null clientId -> INVALID_CLIENT_ID")
  void nullClientIdFailsFast() throws Exception {
    MicrosoftAuth auth = new MicrosoftAuth();
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CLIENT_ID);
    assertTrue(http.sent().isEmpty());
  }

  @Test
  @DisplayName("refresh succeeds with valid refresh token, full exchange chain")
  void refreshSuccess() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    http.on("token", 200, Json.tokenSuccess("ms-at-new", "ms-rt-new", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl-tok", "uhs-1"));
    http.on("xsts/authorize", 200, Json.xsts("xsts-tok"));
    http.on("login_with_xbox", 200, Json.mcToken("mc-at-new", 86400));
    http.on("minecraft/profile", 200, Json.profile("uuid-refresh", "RefreshTest"));

    AuthSession session = auth.refresh("old-refresh-token");
    assertEquals("RefreshTest", session.username());
    assertEquals("uuid-refresh", session.uuid());
    assertEquals("mc-at-new", session.accessToken());
    assertEquals("ms-rt-new", session.refreshToken());
    assertTrue(session.expitesAt().isAfter(java.time.Instant.now()));
  }

  @Test
  @DisplayName("refresh async returns handle and completes successfully")
  void refreshAsyncSuccess() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    http.on("token", 200, Json.tokenSuccess("ms-at-async", "rt-async", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 200, Json.xsts("xsts"));
    http.on("login_with_xbox", 200, Json.mcToken("mc-async", 86400));
    http.on("minecraft/profile", 200, Json.profile("uuid-async", "AsyncUser"));

    AuthHandle handle = auth.refreshAsync("rt-old");
    AuthState terminal = awaitTerminal(handle, 30);
    assertInstanceOf(AuthState.Success.class, terminal);
    AuthSession s = ((AuthState.Success) terminal).session();
    assertEquals("AsyncUser", s.username());
    assertEquals("uuid-async", s.uuid());
  }

  @Test
  @DisplayName("refresh with empty clientId throws AuthException")
  void refreshNoClientId() {
    MicrosoftAuth auth = new MicrosoftAuth();
    AuthException ex = assertThrows(AuthException.class, () -> auth.refresh("rt"));
    assertTrue(ex.getMessage().contains("clientId is not configured"));
  }

  @Test
  @DisplayName("refresh receives invalid_grant -> INVALID_CREDENTIALS")
  void refreshInvalidGrant() {
    MicrosoftAuth auth = newAuth("cid", true);
    http.on("token", 400, Json.error("invalid_grant"));
    AuthException ex = assertThrows(AuthException.class, () -> auth.refresh("bad-rt"));
    assertTrue(ex.getMessage().contains("refresh token invalid"));
  }

  @Test
  @DisplayName("logout is a no-op")
  void logoutNoOp() {
    MicrosoftAuth auth = newAuth("cid", true);
    assertDoesNotThrow(() -> auth.logout("token"));
  }

  @Test
  @DisplayName("device code non-200 -> INVALID_CLIENT_ID")
  void deviceCodeNon200() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(400, Json.error("invalid_client"));
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CLIENT_ID);
  }

  @Test
  @DisplayName("device code malformed JSON -> INVALID_CREDENTIALS")
  void deviceCodeBadJson() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, "not-json{");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("device code fields missing -> INVALID_CREDENTIALS")
  void deviceCodeMissingFields() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, "{\"device_code\":\"dc\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("device code succeeds -> first request hits the device code endpoint")
  void deviceCodeEmitsWaiting() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubFullSuccessChain("ms-at", "ms-rt", "xbl", "uhs", "xsts", "mc-at", "uuid", "Alex");
    awaitTerminal(auth.login(), 30);
    assertEquals(MicrosoftAuth.LEGACY_DEVICE_CODE_URL, http.sent().get(0).uri().toString());
  }

  @Test
  @DisplayName("authorization_pending twice, then success")
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
  @DisplayName("slow_down adds 5s to the interval, flow still succeeds (>= 7s)")
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
    assertTrue(ms >= 7000, "after slow_down the flow needs >= 7000ms but took " + ms + "ms");
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
  @DisplayName("poll succeeds but access_token is missing -> INVALID_CREDENTIALS")
  void tokenMissingAccessToken() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, "{\"refresh_token\":\"rt\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("XBL response without uhs -> INVALID_CREDENTIALS")
  void xblMissingUhs() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, "{\"Token\":\"xbl\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("XSTS non-200 -> INVALID_CREDENTIALS")
  void xstsNon200() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubDeviceCode(200, Json.deviceCode("dc", "uc", 1, 900));
    http.fifo("token", 200, Json.tokenSuccess("ms-at", "ms-rt", 3600));
    http.on("user/authenticate", 200, Json.xbl("xbl", "uhs"));
    http.on("xsts/authorize", 401, "{\"error\":\"XSTS error\"}");
    assertFailed(awaitTerminal(auth.login(), 30), AuthFailureReason.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("MC login without access_token -> INVALID_CREDENTIALS")
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
  @DisplayName("profile without name -> INVALID_CREDENTIALS")
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

  @Test
  @DisplayName("full flow -> Success with the expected session fields")
  void fullSuccessSession() throws Exception {
    MicrosoftAuth auth = newAuth("cid", true);
    stubFullSuccessChain(
        "ms-at", "ms-refresh", "xbl-tok", "UHS-1", "xsts-tok", "mc-access", "uuid-abc", "Notch");

    AuthState s = awaitTerminal(auth.login(), 30);
    assertInstanceOf(AuthState.Success.class, s, "actual: " + s);
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
  @DisplayName("modern mode uses the modern endpoints")
  void modernModeUsesModernUrls() throws Exception {
    MicrosoftAuth auth = newAuth("cid", false);
    stubFullSuccessChain("a", "r", "x", "u", "y", "m", "id", "n");
    awaitTerminal(auth.login(), 30);
    assertTrue(http.sent().get(0).uri().toString().contains("microsoftonline.com"));
  }

  @Test
  @DisplayName("parseError parses correctly and returns null on invalid input")
  void parseErrorDirect() {
    assertEquals(
        "authorization_pending", MicrosoftAuth.parseError(Json.error("authorization_pending")));
    assertNull(MicrosoftAuth.parseError("not-json"));
    assertNull(MicrosoftAuth.parseError("{}"));
  }

  @Test
  @DisplayName("parseTokenSuccess succeeds; a missing access_token throws AbortFlow")
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


  void hitlRealRefresh() throws Exception {
    String clientId = System.getProperty("hitl.clientId");
    if (clientId == null || clientId.isBlank()) {
      clientId = prompt("Enter clientId (00000000402b5328): ");
      if (clientId.isBlank()) {
        clientId = "00000000402b5328";
      }
    }
    String refreshToken = System.getProperty("hitl.rt");
    if (refreshToken == null || refreshToken.isBlank()) {
      refreshToken = prompt("Enter refresh token: ");
    }
    boolean legacy = !"false".equalsIgnoreCase(System.getProperty("hitl.legacy", "true"));
    String finalClientId = clientId.trim();
    String finalRt = refreshToken.trim();
    runHitlRefresh(finalClientId, finalRt, legacy, 300);
  }

  static void runHitlRefresh(String clientId, String refreshToken, boolean legacy, long timeoutSec) throws Exception {
    MicrosoftAuth.setHttpClientForTest(null);
    MicrosoftAuth auth = new MicrosoftAuth();
    auth.setClientID(clientId);
    auth.setLegacy(legacy);

    System.out.println("========== HITL REFRESH ==========");
    System.out.println("clientId    = " + clientId);
    System.out.println("legacy mode = " + legacy);
    System.out.println("timeout     = " + timeoutSec + "s");
    System.out.println("-----------------------------------");

    long t0 = System.currentTimeMillis();
    AuthHandle handle = auth.refreshAsync(refreshToken);
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
      System.out.println("!! timed out, current state: " + handle.state());
      handle.cancel();
      return;
    }

    System.out.println("===================================");
    if (last instanceof AuthState.Success ok) {
      AuthSession s = ok.session();
      System.out.println("refresh succeeded");
      System.out.println("  username    = " + s.username());
      System.out.println("  uuid        = " + s.uuid());
      System.out.println("  expitesAt   = " + s.expitesAt());
      System.out.println("  accessToken = " + mask(s.accessToken()));
      System.out.println("  refreshToken= " + mask(s.refreshToken()));
    } else {
      AuthState.Failed f = (AuthState.Failed) last;
      System.out.println("refresh failed");
      System.out.println("  reason  = " + f.reason());
      System.out.println("  message = " + (f.error() == null ? null : f.error()));
      if (f.cause() != null) {
        f.cause().printStackTrace(System.out);
      }
    }
    System.out.println("total elapsed: " + (System.currentTimeMillis() - t0) + "ms");
  }


  @Test
  @DisplayName("HITL: real device code login (authorize manually in a browser)")
  void hitlRealLogin() throws Exception {
    String clientId = System.getProperty("hitl.clientId");
    if (clientId == null || clientId.isBlank()) {
      clientId = prompt("Enter the Microsoft OAuth clientId: ");
    }
    boolean legacy = !"false".equalsIgnoreCase(System.getProperty("hitl.legacy", "true"));
    String finalClientId = clientId.trim();
    runHitl(finalClientId, legacy, 300);
  }

  public static void main(String[] args) throws Exception {
    String clientId = prompt("Enter the Microsoft OAuth clientId: ");
    String mode = prompt("Which mode? (1=legacy, 2=modern, default 1): ");
    boolean legacy = !"2".equals(mode.trim());
    runHitl(clientId.trim(), legacy, 300);
    runHitlRefresh(clientId.trim(),"M.C516_SN1.0.U.MsaArtifacts.-Cth3afMqYY2xoqHQWC28Urk8lbsguLfmDXrkJ0WYmVlEfhecW9n92rbddyikFvqJ0FJD5X9MaXqyulG70euzaHUzTQAZXxKf8UyTysIgWEx7Zj6sZfs8!UEPgwVm3nlDLUm7YKNWW6fprasB8N5qBey5gT3dowUqNDiLTLHP3JHwbfYXVDOak7VpH4FF1i1fvls1vrXLRYqf9O3dZtibBJywG*LvOpa3a8PJzyaXeARNqFoGJKNg!xIyZQKx336f8uj9RYVcMhaqONtjoYlxS2e7W4DcZXUU3gASAkIk5cUN",true,10);
    System.exit(0);
  }

  static void runHitl(String clientId, boolean legacy, long timeoutSec) throws Exception {
    MicrosoftAuth.setHttpClientForTest(null);
    MicrosoftAuth auth = new MicrosoftAuth();
    auth.setClientID(clientId);
    auth.setLegacy(legacy);

    System.out.println("========== HITL real login ==========");
    System.out.println("clientId = " + clientId);
    System.out.println("mode     = " + (legacy ? "legacy" : "modern"));
    System.out.println("timeout  = " + timeoutSec + "s");
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
      System.out.println("!! timed out, current state: " + handle.state());
      handle.cancel();
      return;
    }

    System.out.println("===================================");
    if (last instanceof AuthState.Success ok) {
      AuthSession s = ok.session();
      System.out.println("login succeeded");
      System.out.println("  username    = " + s.username());
      System.out.println("  uuid        = " + s.uuid());
      System.out.println("  expitesAt   = " + s.expitesAt());
      System.out.println("  accessToken = " + s.accessToken());
      System.out.println("  refreshToken= " + s.refreshToken());
    } else {
      AuthState.Failed f = (AuthState.Failed) last;
      System.out.println("login failed");
      System.out.println("  reason  = " + f.reason());
      System.out.println("  message = " + (f.error() == null ? null : f.error()));
      if (f.cause() != null) {
        f.cause().printStackTrace(System.out);
      }
    }
    System.out.println("total elapsed: " + (System.currentTimeMillis() - t0) + "ms");
  }

  private static void printState(AuthState s, long elapsedMs) {
    String prefix = "[" + elapsedMs + "ms] ";
    if (s == null) {
      System.out.println(prefix + "state: null");
    } else if (s instanceof AuthState.WaitingForUser w) {
      System.out.println(prefix + "⏳ WaitingForUser");
      System.out.println("  message         = " + w.instruction());
      System.out.println("  verificationUri = " + w.url());
      System.out.println("  userCode        = " + w.code());
      System.out.println("  >>> open the URI above in a browser and enter the userCode <<<");
    } else if (s instanceof AuthState.Success ok) {
      System.out.println(prefix + "✅ Success");
      System.out.println(
          "  session = " + ok.session().username() + " (" + ok.session().uuid() + ")");
    } else if (s instanceof AuthState.Failed f) {
      System.out.println(prefix + "❌ Failed reason=" + f.reason());
    } else {
      System.out.println(prefix + "state: " + s);
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
