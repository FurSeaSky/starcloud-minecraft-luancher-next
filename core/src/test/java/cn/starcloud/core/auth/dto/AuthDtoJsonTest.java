package cn.starcloud.core.auth.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the wire names of the auth payloads: a wrong {@code @SerializedName} silently yields null
 * fields.
 */
class AuthDtoJsonTest {

  private static final Gson GSON = new Gson();

  @Test
  void deviceCodeUsesSnakeCaseWireNames() {
    DeviceCodeJson json =
        GSON.fromJson(
            "{\"user_code\":\"ABCD\",\"verification_uri\":\"https://example.invalid\","
                + "\"device_code\":\"dev\",\"expires_in\":900,\"interval\":5,\"message\":\"open\"}",
            DeviceCodeJson.class);

    assertEquals("ABCD", json.userCode());
    assertEquals("https://example.invalid", json.verificationUri());
    assertEquals("dev", json.deviceCode());
    assertEquals(900, json.expiresIn());
    assertEquals(5, json.interval());
    assertEquals("open", json.message());
  }

  @Test
  void tokenSuccessUsesSnakeCaseWireNames() {
    TokenSuccessJson json =
        GSON.fromJson(
            "{\"access_token\":\"a\",\"refresh_token\":\"r\",\"expires_in\":3600,"
                + "\"token_type\":\"Bearer\"}",
            TokenSuccessJson.class);

    assertEquals("a", json.accessToken());
    assertEquals("r", json.refreshToken());
    assertEquals(3600, json.expiresIn());
    assertEquals("Bearer", json.tokenType());
  }

  @Test
  void tokenErrorReadsCodeAndDescription() {
    TokenErrorJson json =
        GSON.fromJson(
            "{\"error\":\"authorization_pending\",\"error_description\":\"wait\"}",
            TokenErrorJson.class);

    assertEquals("authorization_pending", json.error());
    assertEquals("wait", json.errorDescription());
  }

  @Test
  void xblTokenReadsUserHashFromNestedClaims() {
    XblTokenJson json =
        GSON.fromJson(
            "{\"Token\":\"xbl\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"hash\"}]}}",
            XblTokenJson.class);

    assertEquals("xbl", json.token());
    assertEquals(List.of(new XblTokenJson.DisplayClaims.Xui("hash")), json.displayClaims().xui());
  }

  @Test
  void xstsTokenReusesDisplayClaimsOfXbl() {
    XstsTokenJson json =
        GSON.fromJson(
            "{\"Token\":\"xsts\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"hash\"}]}}",
            XstsTokenJson.class);

    assertEquals("xsts", json.token());
    assertEquals("hash", json.displayClaims().xui().get(0).uhs());
  }

  @Test
  void minecraftTokenReadsAccessTokenAndLifetime() {
    MinecraftTokenJson json =
        GSON.fromJson("{\"access_token\":\"mc\",\"expires_in\":86400}", MinecraftTokenJson.class);

    assertEquals("mc", json.accessToken());
    assertEquals(86400, json.expiresIn());
  }

  @Test
  void minecraftProfileReadsIdAndName() {
    MinecraftProfileJson json =
        GSON.fromJson("{\"id\":\"uuid\",\"name\":\"Notch\"}", MinecraftProfileJson.class);

    assertEquals("uuid", json.id());
    assertEquals("Notch", json.name());
  }

  @Test
  void absentFieldsStayNull() {
    TokenErrorJson json = GSON.fromJson("{\"error\":\"slow_down\"}", TokenErrorJson.class);

    assertEquals("slow_down", json.error());
    assertNull(json.errorDescription());
  }

  @Test
  void sessionKeepsEveryComponent() {
    Instant expiresAt = Instant.parse("2026-01-02T03:04:05Z");

    AuthSession session = new AuthSession("Notch", "uuid", "access", "refresh", expiresAt);

    assertEquals("Notch", session.username());
    assertEquals("uuid", session.uuid());
    assertEquals("access", session.accessToken());
    assertEquals("refresh", session.refreshToken());
    assertEquals(expiresAt, session.expitesAt());
  }

  @Test
  void sessionsWithTheSameComponentsAreEqual() {
    AuthSession first = new AuthSession("Notch", "uuid", "access", "refresh", Instant.EPOCH);
    AuthSession second = new AuthSession("Notch", "uuid", "access", "refresh", Instant.EPOCH);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
  }
}
