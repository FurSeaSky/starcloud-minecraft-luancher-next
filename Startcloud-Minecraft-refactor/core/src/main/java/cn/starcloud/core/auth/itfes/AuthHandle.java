package cn.starcloud.core.auth.itfes;

import cn.starcloud.core.auth.dto.AuthSession;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Observable state container for a single login attempt.
 *
 * <p>An implementation owns the state and the result future; it never decides <em>how</em> to log
 * in. Producers publish transitions, consumers observe them, and both sides may sit on different
 * threads.
 */
public interface AuthHandle {

  /**
   * Returns the most recent state.
   *
   * @return the current state, never {@code null}
   */
  AuthState state();

  /**
   * Registers a listener and replays the current state to it immediately.
   *
   * <p>Replaying on subscription lets a late subscriber (for example a UI opened after the flow
   * started) render without polling {@link #state()}.
   *
   * @param listener receives the current state, then every later transition
   */
  void onStateChanged(Consumer<AuthState> listener);

  /**
   * Returns the future that completes when the attempt reaches a terminal state.
   *
   * @return future completing with the session on success, completing exceptionally with an {@link
   *     cn.starcloud.core.auth.exception.AuthException} on failure, or cancelled by {@link
   *     #cancel()}
   */
  CompletableFuture<AuthSession> result();

  /**
   * Abandons the attempt: moves the state to a cancelled failure and interrupts background work.
   *
   * <p>Idempotent. After cancellation every {@code update} is ignored, so a worker that is slow to
   * notice the interrupt cannot overwrite the cancelled state.
   */
  void cancel();
}
