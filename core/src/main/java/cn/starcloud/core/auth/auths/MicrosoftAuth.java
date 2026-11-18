package cn.starcloud.core.auth.auths;

import cn.starcloud.core.auth.*;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MicrosoftAuth implements IAuth {

  public static final Logger LOG = LogManager.getLogger(MicrosoftAuth.class);
  public static final Gson GSON = new Gson();
  public static HttpClient httpClient = HttpClient.newHttpClient();

  public static final String LEGACY_DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
  public static final String LEGACY_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
  public static final String LEGACY_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

  public static final String MODERN_DEVICE_CODE_URL =
      "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
  public static final String MODERN_TOKEN_URL =
      "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
  public static final String MODERN_SCOPE = "XboxLive.signin offline_access";

  public static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
  public static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
  public static final String MC_LOGIN_URL =
      "https://api.minecraftservices.com/authentication/login_with_xbox";
  public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

  public static final long SLOW_DOWN_INCREMENT_SECONDS = 5;

  public volatile String clientId;
  public boolean isLegacy = true;

  /** 测试专用：注入桩客户端；传 null 恢复默认真实客户端。 */
  public static void setHttpClientForTest(HttpClient client) {
    httpClient = (client == null) ? HttpClient.newHttpClient() : client;
  }

  @Override
  public AuthSession refresh(String refreshToken) throws AuthException {
    throw new AuthException("refresh not implemented yet");
  }

  @Override
  public void logout(String accessToken) throws AuthException {
    // no-op: logout not implemented
  }

  public void setClientID(String clientId) {
    this.clientId = clientId;
  }

  public void setLegacy(boolean legacy) {
    this.isLegacy = legacy;
  }

  public static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  @Override
  public AuthHandle login() {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    if (clientId == null || clientId.isEmpty()) {
      LOG.warn("MicrosoftAuth.login: clientId is null/empty");
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CLIENT_ID, new AuthException("clientId is null")));
      return handle;
    }
    Thread worker = Thread.ofVirtual().name("ms-auth-poll").start(() -> runFlow(handle));
    handle.setWorker(worker);
    return handle;
  }

  public void runFlow(DefaultAuthHandle handle) {
    try {
      DeviceCodeJson codeJson = requestDeviceCode(handle);
      handle.update(
          new AuthState.WaitingForUser(
              codeJson.message(), codeJson.verificationUri(), codeJson.userCode()));

      TokenSuccessJson msToken = pollForToken(handle, codeJson);
      XblTokenJson xbl = exchangeXbl(handle, msToken.accessToken());
      String uhs = xbl.displayClaims().xui().get(0).uhs();
      LOG.info("XBL token acquired, uhs={}", uhs);

      XstsTokenJson xsts = exchangeXsts(handle, xbl.token());
      LOG.info("XSTS token acquired");

      MinecraftTokenJson mcToken = loginMinecraft(handle, uhs, xsts.token());
      LOG.info("Minecraft access token acquired, expiresIn={}", mcToken.expiresIn());

      MinecraftProfileJson profile = fetchProfile(handle, mcToken.accessToken());

      Instant expiresAt = Instant.now().plusSeconds(mcToken.expiresIn());
      AuthSession session =
          new AuthSession(
              profile.name(),
              profile.id(),
              mcToken.accessToken(),
              msToken.refreshToken(),
              expiresAt);
      LOG.info("AuthSession assembled: username={}, uuid={}", profile.name(), profile.id());
      handle.update(new AuthState.Success(session));
    } catch (AbortFlow ignored) {
      // state already updated
    } catch (Exception e) {
      LOG.error("Unexpected error in device code flow", e);
      handle.update(new AuthState.Failed(AuthFailureReason.UNKNOWN, e));
    }
  }

  public HttpRequest buildDeviceCodeRequest() {
    String deviceCodeUrl = isLegacy ? LEGACY_DEVICE_CODE_URL : MODERN_DEVICE_CODE_URL;
    String scope = isLegacy ? LEGACY_SCOPE : MODERN_SCOPE;
    return HttpRequest.newBuilder()
        .uri(URI.create(deviceCodeUrl))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(
            HttpRequest.BodyPublishers.ofString(
                "client_id="
                    + encode(clientId)
                    + "&response_type=device_code"
                    + "&scope="
                    + encode(scope)))
        .build();
  }

  public HttpRequest buildTokenRequest(String deviceCode) {
    String tokenUrl = isLegacy ? LEGACY_TOKEN_URL : MODERN_TOKEN_URL;
    return HttpRequest.newBuilder()
        .uri(URI.create(tokenUrl))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(
            HttpRequest.BodyPublishers.ofString(
                "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    + "&client_id="
                    + encode(clientId)
                    + "&device_code="
                    + encode(deviceCode)))
        .build();
  }

  public DeviceCodeJson requestDeviceCode(DefaultAuthHandle handle) {
    HttpRequest req = buildDeviceCodeRequest();

    HttpResponse<String> response;
    try {
      response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      LOG.warn("Device code request IO error", e);
      handle.update(new AuthState.Failed(AuthFailureReason.NETWORK_ERROR, e));
      throw new AbortFlow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.info("Device code request interrupted (cancelled)");
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.CANCELLED, new AuthException("InterruptedException", e)));
      throw new AbortFlow();
    }

    LOG.debug("Device code response: status={}, body={}", response.statusCode(), response.body());

    if (response.statusCode() != 200) {
      LOG.warn("Device code failed: status={}, body={}", response.statusCode(), response.body());
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CLIENT_ID, new AuthException(response.body())));
      throw new AbortFlow();
    }

    DeviceCodeJson codeJson;
    try {
      codeJson = GSON.fromJson(response.body(), DeviceCodeJson.class);
    } catch (JsonSyntaxException e) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS, new AuthException("JsonSyntaxException", e)));
      throw new AbortFlow();
    }

    if (codeJson == null
        || codeJson.deviceCode() == null
        || codeJson.userCode() == null
        || codeJson.expiresIn() <= 0) {
      LOG.warn("Device code json missing required fields: body={}", response.body());
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException("device code json missing fields")));
      throw new AbortFlow();
    }
    return codeJson;
  }

  public TokenSuccessJson pollForToken(DefaultAuthHandle handle, DeviceCodeJson codeJson) {
    Instant deadline = Instant.now().plusSeconds(codeJson.expiresIn());
    long intervalSeconds = Math.max(1, codeJson.interval());

    while (Instant.now().isBefore(deadline)) {
      sleepOrAbort(handle, intervalSeconds);

      HttpResponse<String> response = sendTokenPoll(handle, codeJson.deviceCode());
      LOG.debug("Token poll response: status={}, body={}", response.statusCode(), response.body());

      if (response.statusCode() == 200) {
        TokenSuccessJson msToken = parseTokenSuccess(handle, response.body());
        LOG.info("Microsoft access token acquired, expiresIn={}", msToken.expiresIn());
        return msToken;
      }

      String error = parseError(response.body());
      if ("authorization_pending".equals(error)) {
        LOG.debug("authorization_pending, keep polling");
        continue;
      }
      if ("slow_down".equals(error)) {
        intervalSeconds += SLOW_DOWN_INCREMENT_SECONDS;
        LOG.debug("slow_down, interval now {}s", intervalSeconds);
        continue;
      }
      if ("expired_token".equals(error)) {
        LOG.info("device code expired");
        handle.update(
            new AuthState.Failed(
                AuthFailureReason.DEVICE_CODE_EXPIRED, new AuthException("device code expired")));
        throw new AbortFlow();
      }
      if ("authorization_declined".equals(error)) {
        LOG.info("authorization declined by user");
        handle.update(
            new AuthState.Failed(
                AuthFailureReason.CANCELLED, new AuthException("authorization declined")));
        throw new AbortFlow();
      }

      LOG.warn("Unknown token poll error: {}", error);
    }

    LOG.info("polling deadline reached");
    handle.update(
        new AuthState.Failed(
            AuthFailureReason.DEVICE_CODE_EXPIRED, new AuthException("polling deadline reached")));
    throw new AbortFlow();
  }

  public void sleepOrAbort(DefaultAuthHandle handle, long intervalSeconds) {
    try {
      TimeUnit.SECONDS.sleep(intervalSeconds);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.info("Polling sleep interrupted (cancelled)");
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.CANCELLED, new AuthException("InterruptedException", e)));
      throw new AbortFlow();
    }
  }

  public HttpResponse<String> sendTokenPoll(DefaultAuthHandle handle, String deviceCode) {
    HttpRequest req = buildTokenRequest(deviceCode);
    try {
      return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      LOG.warn("Token poll IO error", e);
      handle.update(new AuthState.Failed(AuthFailureReason.NETWORK_ERROR, e));
      throw new AbortFlow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.info("Token poll interrupted (cancelled)");
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.CANCELLED, new AuthException("InterruptedException", e)));
      throw new AbortFlow();
    }
  }

  public TokenSuccessJson parseTokenSuccess(DefaultAuthHandle handle, String body) {
    TokenSuccessJson msToken;
    try {
      msToken = GSON.fromJson(body, TokenSuccessJson.class);
    } catch (JsonSyntaxException e) {
      LOG.warn("Token success json parse failed, body={}", body);
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException("TokenSuccessJson parse failed", e)));
      throw new AbortFlow();
    }
    if (msToken == null || msToken.accessToken() == null) {
      LOG.warn("Token success json missing access_token, body={}", body);
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS, new AuthException("access_token is null")));
      throw new AbortFlow();
    }
    return msToken;
  }

  public XblTokenJson exchangeXbl(DefaultAuthHandle handle, String msAccessToken) {
    String body =
        "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
            + "\"RpsTicket\":\"d="
            + msAccessToken
            + "\"},"
            + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
    XblTokenJson xbl = sendJson(handle, buildJsonPost(XBL_URL, body), XblTokenJson.class, "XBL");
    if (xbl == null
        || xbl.token() == null
        || xbl.displayClaims() == null
        || xbl.displayClaims().xui() == null
        || xbl.displayClaims().xui().isEmpty()
        || xbl.displayClaims().xui().get(0).uhs() == null) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException("XBL token missing Token or uhs")));
      throw new AbortFlow();
    }
    return xbl;
  }

  public XstsTokenJson exchangeXsts(DefaultAuthHandle handle, String xblToken) {
    String body =
        "{\"Properties\":{\"SandboxId\":\"RETAIL\","
            + "\"UserTokens\":[\""
            + xblToken
            + "\"]},"
            + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
    XstsTokenJson xsts =
        sendJson(handle, buildJsonPost(XSTS_URL, body), XstsTokenJson.class, "XSTS");
    if (xsts == null || xsts.token() == null) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS, new AuthException("XSTS token is null")));
      throw new AbortFlow();
    }
    return xsts;
  }

  public MinecraftTokenJson loginMinecraft(DefaultAuthHandle handle, String uhs, String xstsToken) {
    String body = "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}";
    MinecraftTokenJson mcToken =
        sendJson(
            handle, buildJsonPost(MC_LOGIN_URL, body), MinecraftTokenJson.class, "Minecraft login");
    if (mcToken == null || mcToken.accessToken() == null) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException("Minecraft access_token is null")));
      throw new AbortFlow();
    }
    return mcToken;
  }

  public MinecraftProfileJson fetchProfile(DefaultAuthHandle handle, String mcAccessToken) {
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(MC_PROFILE_URL))
            .header("Authorization", "Bearer " + mcAccessToken)
            .GET()
            .build();
    MinecraftProfileJson profile = sendJson(handle, req, MinecraftProfileJson.class, "Profile");
    if (profile == null || profile.id() == null || profile.name() == null) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS, new AuthException("profile missing id/name")));
      throw new AbortFlow();
    }
    return profile;
  }

  public HttpRequest buildJsonPost(String url, String body) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  public <T> T sendJson(DefaultAuthHandle handle, HttpRequest req, Class<T> type, String step) {
    HttpResponse<String> response;
    try {
      response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      handle.update(new AuthState.Failed(AuthFailureReason.NETWORK_ERROR, e));
      throw new AbortFlow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.CANCELLED, new AuthException("InterruptedException", e)));
      throw new AbortFlow();
    }
    LOG.debug("{} response: status={}, body={}", step, response.statusCode(), response.body());
    if (response.statusCode() != 200) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException(
                  step + " failed: " + response.statusCode() + " " + response.body())));
      throw new AbortFlow();
    }
    try {
      return GSON.fromJson(response.body(), type);
    } catch (JsonSyntaxException e) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException(step + " json parse failed", e)));
      throw new AbortFlow();
    }
  }

  public static String parseError(String body) {
    try {
      TokenErrorJson err = GSON.fromJson(body, TokenErrorJson.class);
      return err == null ? null : err.error();
    } catch (JsonSyntaxException e) {
      return null;
    }
  }
}
