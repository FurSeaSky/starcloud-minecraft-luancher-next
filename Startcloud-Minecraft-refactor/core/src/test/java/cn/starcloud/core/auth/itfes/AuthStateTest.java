package cn.starcloud.core.auth.itfes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.enums.AuthFailureReason;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Callers pattern-match on these records, so their components and equality are part of the
 * contract.
 */
class AuthStateTest {

  @Test
  void pendingCarriesNoData() {
    assertEquals(new AuthState.Pending(), new AuthState.Pending());
  }

  @Test
  void waitingForUserExposesTheThreeThingsTheUserNeeds() {
    AuthState.WaitingForUser state =
        new AuthState.WaitingForUser("open the page", "https://example.invalid", "ABCD-1234");

    assertEquals("open the page", state.instruction());
    assertEquals("https://example.invalid", state.url());
    assertEquals("ABCD-1234", state.code());
  }

  @Test
  void successWrapsTheSession() {
    AuthSession session = new AuthSession("Notch", "uuid", "at", "rt", Instant.EPOCH);
    AuthState.Success state = new AuthState.Success(session);

    assertSame(session, state.session());
  }

  @Test
  void failedExposesReasonThroughBothAccessors() {
    AuthFailureReason reason = AuthFailureReason.NETWORK_ERROR;
    AuthState.Failed state = new AuthState.Failed(reason, null);

    assertEquals(reason, state.reason());
    assertEquals(reason, state.error());
    assertNull(state.cause());
  }

  @Test
  void failedKeepsTheCause() {
    Throwable cause = new IllegalStateException("boom");
    AuthState.Failed state = new AuthState.Failed(AuthFailureReason.UNKNOWN, cause);

    assertSame(cause, state.cause());
  }

  @Test
  void statesWithDifferentPayloadsAreNotEqual() {
    assertNotEquals(
        new AuthState.Success(new AuthSession("a", "u", "at", "rt", Instant.EPOCH)),
        new AuthState.Success(new AuthSession("b", "u", "at", "rt", Instant.EPOCH)));
    assertNotEquals(
        new AuthState.Failed(AuthFailureReason.NETWORK_ERROR, null),
        new AuthState.Failed(AuthFailureReason.CANCELLED, null));
  }
}
