package cn.starcloud.core.auth.auths;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.dto.DeviceCodeJson;
import cn.starcloud.core.auth.dto.MinecraftProfileJson;
import cn.starcloud.core.auth.dto.MinecraftTokenJson;
import cn.starcloud.core.auth.dto.TokenErrorJson;
import cn.starcloud.core.auth.dto.TokenSuccessJson;
import cn.starcloud.core.auth.dto.XblTokenJson;
import cn.starcloud.core.auth.dto.XstsTokenJson;
import cn.starcloud.core.auth.enums.AuthFailureReason;
import cn.starcloud.core.auth.exception.AbortFlow;
import cn.starcloud.core.auth.exception.AuthException;
import cn.starcloud.core.auth.itfes.AuthHandle;
import cn.starcloud.core.auth.itfes.AuthState;
import cn.starcloud.core.auth.itfes.IAuth;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Microsoft account login through the OAuth 2.0 device code flow.
 *
 * <p>The flow is a chain of token exchanges: Microsoft token, Xbox Live token, XSTS token,
 * Minecraft token, then the profile. Each step feeds the next, and the whole chain runs on a
 * virtual thread so {@link #login()} returns immediately with a handle the caller can observe or
 * cancel.
 *
 * <p>Configure {@link #clientId} (required) and {@link #isLegacy} before logging in.
 */
public class MicrosoftAuth implements IAuth {

  /** Logger of the flow; public so callers can route its output with their own configuration. */
  public static final Logger LOG = LoggerFactory.getLogger(MicrosoftAuth.class);

  /** Shared parser with default settings, so response mapping does not allocate per request. */
  public static final Gson GSON = new Gson();

  /**
   * Client used for every request of the flow.
   *
   * <p>Static and mutable so tests can install a stub; production code should leave the default.
   */
  public static HttpClient httpClient = HttpClient.newHttpClient();

  /** live.com device code endpoint, used while {@link #isLegacy} is {@code true}. */
  public static final String LEGACY_DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";

  /** live.com token endpoint, used while {@link #isLegacy} is {@code true}. */
  public static final String LEGACY_TOKEN_URL = "https://login.live.com/oauth20_token.srf";

  /** Scope requested from the legacy endpoint; grants the Xbox Live audience directly. */
  public static final String LEGACY_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

  /** microsoftonline.com device code endpoint, used while {@link #isLegacy} is {@code false}. */
  public static final String MODERN_DEVICE_CODE_URL =
      "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";

  /** microsoftonline.com token endpoint, used while {@link #isLegacy} is {@code false}. */
  public static final String MODERN_TOKEN_URL =
      "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

  /**
   * Scope requested from the modern endpoint; {@code offline_access} is what yields a refresh
   * token.
   */
  public static final String MODERN_SCOPE = "XboxLive.signin offline_access";

  /** Xbox Live user authentication endpoint. */
  public static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";

  /** XSTS authorization endpoint, the last step before a Minecraft token. */
  public static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";

  /** Minecraft services endpoint that trades an XSTS token for a Minecraft access token. */
  public static final String MC_LOGIN_URL =
      "https://api.minecraftservices.com/authentication/login_with_xbox";

  /** Minecraft services endpoint returning the profile (name and uuid) of the token owner. */
  public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

  /** Seconds added to the poll interval when the provider answers {@code slow_down}. */
  public static final long SLOW_DOWN_INCREMENT_SECONDS = 5;

  /** Microsoft client id; {@link #login()} fails fast when it is null or empty. */
  public volatile String clientId;

  /**
   * Whether to use the legacy live.com endpoints; mirrors what the configured client id accepts.
   */
  public boolean isLegacy = true;

  /** Creates an unconfigured instance; set {@link #clientId} before calling {@link #login()}. */
  public MicrosoftAuth() {}

  /**
   * Replaces the HTTP client used by the flow.
   *
   * @param client stub client, or {@code null} to restore a default client
   */
  public static void setHttpClientForTest(HttpClient client) {
    httpClient = (client == null) ? HttpClient.newHttpClient() : client;
  }

  /**
   * {@inheritDoc}
   *
   * @throws AuthException always; renewal is not implemented yet
   */
  /**
   * Refresh Microsoft authentication session using an existing refresh token.
   *
   * <p>This path does not require user interaction. It exchanges the refresh token for a new
   * Microsoft access token, then re-runs the XBL -> XSTS -> Minecraft token chain and fetches the
   * player profile. A new refresh token will be returned; the old one becomes invalid.
   *
   * @param refreshToken valid refresh token obtained from a previous successful login
   * @return a fresh {@link AuthSession} with new access token and new refresh token
   * @throws AuthException if clientId is missing, network fails, token is invalid/expired, or any
   *     step in the Xbox/Minecraft exchange rejects the request
   */
  @Override
  public AuthSession refresh(String refreshToken) throws AuthException {
    if (clientId == null || clientId.isEmpty()) {
      throw new AuthException("clientId is not configured");
    }
    DefaultAuthHandle handle = new DefaultAuthHandle();
    try {
      TokenSuccessJson msToken = refreshMicrosoftToken(handle, refreshToken);
      XblTokenJson xbl = exchangeXbl(handle, msToken.accessToken());
      String uhs = xbl.displayClaims().xui().get(0).uhs();
      XstsTokenJson xsts = exchangeXsts(handle, xbl.token());
      MinecraftTokenJson mcToken = loginMinecraft(handle, uhs, xsts.token());
      MinecraftProfileJson profile = fetchProfile(handle, mcToken.accessToken());

      Instant expiresAt = Instant.now().plusSeconds(mcToken.expiresIn());
      AuthSession session =
          new AuthSession(
              profile.name(),
              profile.id(),
              mcToken.accessToken(),
              msToken.refreshToken(),
              expiresAt);
      LOG.info("Refresh success, username={}, uuid={}", profile.name(), profile.id());
      return session;
    } catch (AbortFlow ignored) {
      AuthState state = handle.state();
      if (state instanceof AuthState.Failed failed) {
        Throwable cause = failed.cause();
        if (cause instanceof AuthException ae) {
          throw ae;
        } else {
          throw new AuthException("refresh failed", cause);
        }
      }
      throw new AuthException("refresh aborted");
    }
  }

  /**
   * Exchange refresh_token for a new Microsoft access token and refresh token.
   *
   * @param handle state handle for reporting failures
   * @param refreshToken refresh token from prior authentication
   * @return parsed token response containing new access_token and refresh_token
   * @throws AbortFlow on network error, interrupt, non-200 status or invalid response
   */
  private TokenSuccessJson refreshMicrosoftToken(DefaultAuthHandle handle, String refreshToken)
      throws AbortFlow {
    String tokenUrl = isLegacy ? LEGACY_TOKEN_URL : MODERN_TOKEN_URL;
    String scope = isLegacy ? LEGACY_SCOPE : MODERN_SCOPE;

    String body =
        "grant_type=refresh_token"
            + "&client_id="
            + encode(clientId)
            + "&refresh_token="
            + encode(refreshToken)
            + "&scope="
            + encode(scope);

    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpResponse<String> response;
    try {
      response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      LOG.warn("Refresh microsoft token IO error", e);
      handle.update(new AuthState.Failed(AuthFailureReason.NETWORK_ERROR, e));
      throw new AbortFlow();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.info("Refresh interrupted");
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.CANCELLED, new AuthException("refresh interrupted", e)));
      throw new AbortFlow();
    }

    if (response.statusCode() != 200) {
      String err = parseError(response.body());
      LOG.warn(
          "Refresh failed, status={}, error={}, body={}",
          response.statusCode(),
          err,
          response.body());
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CREDENTIALS,
              new AuthException("refresh token invalid: " + response.body())));
      throw new AbortFlow();
    }

    return parseTokenSuccess(handle, response.body());
  }

  /**
   * Asynchronous refresh version, consistent with {@link #login()} style. Runs on a virtual thread
   * and returns immediately with an observable handle.
   *
   * @param refreshToken existing refresh token
   * @return {@link AuthHandle} tracking refresh progress or terminal state
   */
  public AuthHandle refreshAsync(String refreshToken) {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    if (clientId == null || clientId.isEmpty()) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_CLIENT_ID, new AuthException("clientId empty")));
      return handle;
    }
    Thread worker =
        Thread.ofVirtual()
            .name("ms-auth-refresh")
            .start(
                () -> {
                  try {
                    AuthSession session = refresh(refreshToken);
                    handle.update(new AuthState.Success(session));
                  } catch (AuthException e) {
                    handle.update(new AuthState.Failed(AuthFailureReason.INVALID_CREDENTIALS, e));
                  }
                });
    handle.setWorker(worker);
    return handle;
  }

  /**
   * {@inheritDoc}
   *
   * <p>No-op: the flow has no revocation call yet, so the local session is simply dropped.
   */
  @Override
  public void logout(String accessToken) throws AuthException {}

  /**
   * Sets the Microsoft client id used by the next login.
   *
   * @param clientId client id of the registered application
   */
  public void setClientID(String clientId) {
    this.clientId = clientId;
  }

  /**
   * Selects the endpoint family.
   *
   * @param legacy {@code true} for the live.com endpoints, {@code false} for microsoftonline.com
   */
  public void setLegacy(boolean legacy) {
    this.isLegacy = legacy;
  }

  /**
   * Percent-encodes a value for an {@code application/x-www-form-urlencoded} body.
   *
   * @param s value to encode
   * @return the encoded value
   */
  public static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  /**
   * Starts the login and returns immediately.
   *
   * <p>Side effect: when a client id is configured, starts a virtual thread named {@code
   * ms-auth-poll} that drives the flow and reports through the returned handle.
   *
   * @return handle reporting {@link AuthState.WaitingForUser}, then success or failure; already
   *     failed with {@link AuthFailureReason#INVALID_CLIENT_ID} when no client id is set
   */
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

  /**
   * Runs the whole chain on the calling thread and reports every step to {@code handle}.
   *
   * <p>Every step reports its own failure before aborting, so the terminal state is already set
   * when {@link AbortFlow} unwinds here.
   *
   * @param handle handle receiving the transitions
   */
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
      // The failing step already reported the terminal state.
    } catch (Exception e) {
      LOG.error("Unexpected error in device code flow", e);
      handle.update(new AuthState.Failed(AuthFailureReason.UNKNOWN, e));
    }
  }

  /**
   * Builds the device code request for the configured endpoint family.
   *
   * @return the unsigned form-encoded request
   */
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

  /**
   * Builds a token poll request for the given device code.
   *
   * @param deviceCode code returned by the device code endpoint
   * @return the unsigned form-encoded request
   */
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

  /**
   * Requests a device code, the first step of the flow.
   *
   * @param handle handle to report failures to
   * @return the parsed device code response
   * @throws AbortFlow when the request fails, the status is not 200, or required fields are
   *     missing; the handle has already been moved to the matching failure state
   */
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

  /**
   * Polls the token endpoint until the user finishes, the code expires, or the poll fails.
   *
   * <p>Blocks for as long as the flow lives, honouring the provider's interval and its {@code
   * slow_down} back-off. Only a successful poll returns; every other outcome throws.
   *
   * @param handle handle to report failures to
   * @param codeJson device code response that sets the deadline and the initial interval
   * @return the Microsoft token response
   * @throws AbortFlow on cancellation, expiration, rejection, or network failure
   */
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

  /**
   * Sleeps between polls, turning an interrupt into a cancellation.
   *
   * @param handle handle to report failures to
   * @param intervalSeconds seconds to sleep
   * @throws AbortFlow when the sleep is interrupted; the interrupt flag is restored
   */
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

  /**
   * Sends one token poll.
   *
   * @param handle handle to report failures to
   * @param deviceCode code being polled
   * @return the raw response, whatever its status
   * @throws AbortFlow on network failure or interruption
   */
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

  /**
   * Parses a successful token response.
   *
   * @param handle handle to report failures to
   * @param body response body of a 200 poll
   * @return the parsed token
   * @throws AbortFlow when the body is not valid JSON or carries no access token
   */
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

  /**
   * Exchanges the Microsoft token for an Xbox Live token.
   *
   * @param handle handle to report failures to
   * @param msAccessToken Microsoft access token
   * @return the Xbox Live token response
   * @throws AbortFlow when the exchange fails or the response lacks a token or user hash
   */
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

  /**
   * Exchanges the Xbox Live token for an XSTS token.
   *
   * @param handle handle to report failures to
   * @param xblToken Xbox Live token
   * @return the XSTS token response
   * @throws AbortFlow when the exchange fails or the response lacks a token
   */
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

  /**
   * Exchanges the XSTS token for a Minecraft token.
   *
   * @param handle handle to report failures to
   * @param uhs user hash from the Xbox Live claims
   * @param xstsToken XSTS token
   * @return the Minecraft token response
   * @throws AbortFlow when the exchange fails or the response lacks an access token
   */
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

  /**
   * Fetches the profile of the account owning the Minecraft token.
   *
   * @param handle handle to report failures to
   * @param mcAccessToken Minecraft access token
   * @return the profile response
   * @throws AbortFlow when the request fails or the profile lacks an id or name
   */
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

  /**
   * Builds a JSON POST request.
   *
   * @param url absolute target URL
   * @param body JSON body, sent as-is
   * @return the request
   */
  public HttpRequest buildJsonPost(String url, String body) {
    return HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  /**
   * Sends a request, checks the status, and parses the JSON body.
   *
   * @param <T> response type
   * @param handle handle to report failures to
   * @param req request to send
   * @param type class to deserialise the body into
   * @param step step name used in log and error messages
   * @return the parsed body, possibly {@code null} when the body is JSON {@code null}
   * @throws AbortFlow on network failure, interruption, a non-200 status, or malformed JSON
   */
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

  /**
   * Extracts the error code from a token endpoint body.
   *
   * @param body response body, expected to be a {@link TokenErrorJson}
   * @return the error code, or {@code null} when the body is unparsable or has no error
   */
  public static String parseError(String body) {
    try {
      TokenErrorJson err = GSON.fromJson(body, TokenErrorJson.class);
      return err == null ? null : err.error();
    } catch (JsonSyntaxException e) {
      return null;
    }
  }
}
