package cn.starcloud.core.auth;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultAuthHandle implements AuthHandle {

  private final CopyOnWriteArrayList<Consumer<AuthState>> listeners = new CopyOnWriteArrayList<>();
  private final CompletableFuture<AuthSession> result = new CompletableFuture<>();
  private volatile AuthState state = new AuthState.Pending();
  private volatile boolean cancelled = false;
  private volatile Thread worker;

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

  public boolean isCancelled() {
    return cancelled;
  }

  private void notifyListeners(AuthState newState) {
    for (Consumer<AuthState> listener : listeners) {
      listener.accept(newState);
    }
  }
}
