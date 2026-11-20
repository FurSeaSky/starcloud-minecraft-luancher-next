package cn.starcloud.core.auth.auths;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.enums.AuthFailureReason;
import cn.starcloud.core.auth.exception.AuthException;
import cn.starcloud.core.auth.itfes.AuthHandle;
import cn.starcloud.core.auth.itfes.AuthState;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * {@link AuthHandle} that any flow can drive by calling {@link #update(AuthState)}.
 *
 * <p>Listeners are held in a {@link CopyOnWriteArrayList} because registration outnumbers
 * transitions and listeners may register from another thread while a transition is being delivered.
 */
public class DefaultAuthHandle implements AuthHandle {

  private final CopyOnWriteArrayList<Consumer<AuthState>> listeners = new CopyOnWriteArrayList<>();
  private final CompletableFuture<AuthSession> result = new CompletableFuture<>();
  private volatile AuthState state = new AuthState.Pending();
  private volatile boolean cancelled = false;
  private volatile Thread worker;

  /**
   * Creates a handle in the {@link AuthState.Pending} state with no listeners and a fresh future.
   */
  public DefaultAuthHandle() {}

  /**
   * Registers the thread that runs the flow, so {@link #cancel()} can interrupt it.
   *
   * @param worker background thread driving the flow, may be {@code null}
   */
  public void setWorker(Thread worker) {
    this.worker = worker;
  }

  @Override
  public AuthState state() {
    return state;
  }

  @Override
  public void onStateChanged(Consumer<AuthState> listener) {
    listeners.add(listener);
    listener.accept(state);
  }

  /**
   * Publishes a transition and, for terminal states, settles the result future.
   *
   * <p>Ignored once the handle is cancelled, so a worker that keeps running after {@link #cancel()}
   * cannot resurrect the flow.
   *
   * @param newState the new state; {@link AuthState.Success} completes {@link #result()} with its
   *     session, {@link AuthState.Failed} completes it exceptionally with an {@link AuthException}
   *     named after the reason
   */
  public void update(AuthState newState) {
    if (cancelled) {
      return;
    }
    this.state = newState;
    notifyListeners(newState);
    if (newState instanceof AuthState.Success(AuthSession session)) {
      result.complete(session);
    } else if (newState instanceof AuthState.Failed(AuthFailureReason reason, Throwable cause)) {
      result.completeExceptionally(new AuthException(reason.name(), cause));
    }
  }

  @Override
  public CompletableFuture<AuthSession> result() {
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Cancels the result future with interruption and interrupts the registered worker, if any.
   */
  @Override
  public void cancel() {
    if (cancelled) {
      return;
    }
    cancelled = true;
    AuthState cancelledState = new AuthState.Failed(AuthFailureReason.CANCELLED, null);
    this.state = cancelledState;
    notifyListeners(cancelledState);
    result.cancel(true);
    Thread w = worker;
    if (w != null) {
      w.interrupt();
    }
  }

  /**
   * Tells whether {@link #cancel()} has been called.
   *
   * @return {@code true} once cancelled; further updates are ignored
   */
  public boolean isCancelled() {
    return cancelled;
  }

  private void notifyListeners(AuthState newState) {
    for (Consumer<AuthState> listener : listeners) {
      listener.accept(newState);
    }
  }
}
