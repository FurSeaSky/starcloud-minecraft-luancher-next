package cn.starcloud.core.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.starcloud.core.auth.auths.OfflineAuth;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OfflineAuthTest {

  /** Bounded so a broken implementation fails instead of blocking the build. */
  private static final long TIMEOUT_SECONDS = 5;

  private static AuthState loginWith(String username) {
    OfflineAuth auth = new OfflineAuth();
    auth.setUsername(username);
    return auth.login().state();
  }

  private static AuthState.Success loginExpectingSuccess(String username) {
    return assertInstanceOf(AuthState.Success.class, loginWith(username));
  }

  private static AuthState.Failed loginExpectingFailure(String username) {
    return assertInstanceOf(AuthState.Failed.class, loginWith(username));
  }

  @Test
  void derivesUuidFromUsername() {
    AuthSession session = loginExpectingSuccess("Notch").session();

    assertEquals("Notch", session.username());
    assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", session.uuid());
  }

  @Test
  void uuidIsStableAcrossInstances() {
    String first = loginExpectingSuccess("Steve").session().uuid();
    String second = loginExpectingSuccess("Steve").session().uuid();

    assertEquals(first, second);
  }

  @Test
  void uuidDiffersByUsername() {
    String first = loginExpectingSuccess("Steve").session().uuid();
    String second = loginExpectingSuccess("Alex").session().uuid();

    assertNotEquals(first, second);
  }

  @Test
  void uuidOfMixedCharsetName() {
    assertEquals(
        "b1871417-91dd-3a35-a921-fc6ec0506fdf", loginExpectingSuccess("a_b-c").session().uuid());
  }

  @Test
  void issuesPlaceholderTokensWithNonExpiringSession() {
    AuthSession session = loginExpectingSuccess("Notch").session();

    assertEquals("0", session.accessToken());
    assertEquals("0", session.refreshToken());
    assertEquals(Instant.MAX, session.expitesAt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ab", "0123456789abcdef"})
  void acceptsBoundaryLengths(String username) {
    assertEquals(username, loginExpectingSuccess(username).session().username());
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "0123456789abcdefg"})
  void rejectsOutOfRangeLengths(String username) {
    AuthState.Failed failed = loginExpectingFailure(username);

    assertEquals(AuthFailureReason.INVALID_USERNAME_LENGTH, failed.reason());
  }

  @Test
  void rejectsNullUsername() {
    assertEquals(AuthFailureReason.INVALID_USERNAME_LENGTH, loginExpectingFailure(null).reason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   "})
  void rejectsBlankUsername(String username) {
    assertEquals(
        AuthFailureReason.INVALID_USERNAME_LENGTH, loginExpectingFailure(username).reason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ab c", "abc.", "玩家", "café", "a/b", "ab\t"})
  void rejectsInvalidCharset(String username) {
    AuthState.Failed failed = loginExpectingFailure(username);

    assertEquals(AuthFailureReason.INVALID_USERNAME_CHARSET, failed.reason());
  }

  @ParameterizedTest
  @ValueSource(strings = {"aA0_-", "ZZZZZZZZZZZZZZZZ"})
  void acceptsAllAllowedCharacters(String username) {
    loginExpectingSuccess(username);
  }

  @Test
  void failureCarriesCause() {
    AuthState.Failed failed = loginExpectingFailure("a");

    assertInstanceOf(AuthException.class, failed.cause());
  }

  @Test
  void failedLoginCompletesResultExceptionally() {
    OfflineAuth auth = new OfflineAuth();
    auth.setUsername("ab c");

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () -> auth.login().result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

    AuthException cause = assertInstanceOf(AuthException.class, thrown.getCause());
    assertEquals(AuthFailureReason.INVALID_USERNAME_CHARSET.name(), cause.getMessage());
  }

  @Test
  void successfulLoginCompletesResult() throws Exception {
    OfflineAuth auth = new OfflineAuth();
    auth.setUsername("Notch");

    AuthSession session = auth.login().result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

    assertEquals("Notch", session.username());
    assertTrue(
        session.uuid().matches("[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}"));
  }

  @Test
  void refreshReturnsNullBecauseOfflineSessionsNeverExpire() {
    OfflineAuth auth = new OfflineAuth();
    auth.setUsername("Notch");

    assertNull(auth.refresh("0"));
  }

  @Test
  void logoutAcceptsAnyTokenAndLeavesLoginUsable() {
    OfflineAuth auth = new OfflineAuth();
    auth.setUsername("Notch");

    assertDoesNotThrow(() -> auth.logout("not-a-real-token"));

    assertInstanceOf(AuthState.Success.class, auth.login().state());
  }

  @Test
  void repeatedLoginsAreIndependent() {
    OfflineAuth auth = new OfflineAuth();
    auth.setUsername("Notch");

    AuthHandle first = auth.login();
    auth.setUsername("Steve");
    AuthHandle second = auth.login();

    assertEquals(
        "Notch", assertInstanceOf(AuthState.Success.class, first.state()).session().username());
    assertEquals(
        "Steve", assertInstanceOf(AuthState.Success.class, second.state()).session().username());
  }
}
