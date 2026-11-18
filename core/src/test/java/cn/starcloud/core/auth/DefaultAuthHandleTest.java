package cn.starcloud.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class DefaultAuthHandleTest {

  private static final AuthSession SESSION =
      new AuthSession("Notch", "uuid", "access", "refresh", Instant.EPOCH);

  /** Bounded so a broken implementation fails instead of blocking the build. */
  private static final long TIMEOUT_SECONDS = 5;

  @Test
  void startsPending() {
    assertInstanceOf(AuthState.Pending.class, new DefaultAuthHandle().state());
  }

  @Test
  void listenerReceivesCurrentStateOnSubscribe() {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    List<AuthState> seen = new ArrayList<>();

    handle.onStateChanged(seen::add);

    assertEquals(1, seen.size());
    assertInstanceOf(AuthState.Pending.class, seen.get(0));
  }

  @Test
  void updateNotifiesListenersInOrder() {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    List<AuthState> seen = new ArrayList<>();
    handle.onStateChanged(seen::add);

    handle.update(new AuthState.WaitingForUser("open browser", "https://example.invalid", "ABC"));
    handle.update(new AuthState.Success(SESSION));

    assertEquals(3, seen.size());
    assertInstanceOf(AuthState.Pending.class, seen.get(0));
    assertInstanceOf(AuthState.WaitingForUser.class, seen.get(1));
    assertInstanceOf(AuthState.Success.class, seen.get(2));
  }

  @Test
  void updateReplacesState() {
    DefaultAuthHandle handle = new DefaultAuthHandle();

    handle.update(new AuthState.Success(SESSION));

    assertSame(SESSION, assertInstanceOf(AuthState.Success.class, handle.state()).session());
  }

  @Test
  void successCompletesResult() throws Exception {
    DefaultAuthHandle handle = new DefaultAuthHandle();

    handle.update(new AuthState.Success(SESSION));

    assertSame(SESSION, handle.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  void failureCompletesResultExceptionallyWithReason() {
    NullPointerException cause = new NullPointerException("boom");
    DefaultAuthHandle handle = new DefaultAuthHandle();

    handle.update(new AuthState.Failed(AuthFailureReason.NETWORK_ERROR, cause));

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class, () -> handle.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    AuthException actual = assertInstanceOf(AuthException.class, thrown.getCause());
    assertEquals(AuthFailureReason.NETWORK_ERROR.name(), actual.getMessage());
    assertSame(cause, actual.getCause());
  }

  @Test
  void pendingStateDoesNotCompleteResult() {
    DefaultAuthHandle handle = new DefaultAuthHandle();

    handle.update(new AuthState.Pending());

    assertFalse(handle.result().isDone());
  }

  @Test
  void cancelCancelsResult() {
    DefaultAuthHandle handle = new DefaultAuthHandle();

    handle.cancel();

    assertTrue(handle.isCancelled());
    assertTrue(handle.result().isCancelled());
    assertThrows(
        CancellationException.class, () -> handle.result().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
  }

  @Test
  void updateAfterCancelIsIgnored() {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    handle.cancel();
    List<AuthState> seen = new ArrayList<>();
    handle.onStateChanged(seen::add);

    handle.update(new AuthState.Success(SESSION));

    AuthState.Failed state = assertInstanceOf(AuthState.Failed.class, handle.state());
    assertEquals(AuthFailureReason.CANCELLED, state.reason());
    assertEquals(1, seen.size());
    assertInstanceOf(AuthState.Failed.class, seen.get(0));
  }

  @Test
  void listenersAddedAfterCancelStillSeeLastState() {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    handle.cancel();
    List<AuthState> seen = new ArrayList<>();

    handle.onStateChanged(seen::add);

    assertEquals(1, seen.size());
    AuthState.Failed state = assertInstanceOf(AuthState.Failed.class, seen.get(0));
    assertEquals(AuthFailureReason.CANCELLED, state.reason());
  }

  @Test
  void multipleListenersAllReceiveUpdate() {
    DefaultAuthHandle handle = new DefaultAuthHandle();
    List<AuthState> first = new ArrayList<>();
    List<AuthState> second = new ArrayList<>();
    handle.onStateChanged(first::add);
    handle.onStateChanged(second::add);

    handle.update(new AuthState.Success(SESSION));

    assertEquals(2, first.size());
    assertEquals(2, second.size());
    assertInstanceOf(AuthState.Success.class, first.get(1));
    assertInstanceOf(AuthState.Success.class, second.get(1));
  }
}
