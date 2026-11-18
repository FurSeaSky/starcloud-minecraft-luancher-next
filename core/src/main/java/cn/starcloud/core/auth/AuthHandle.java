package cn.starcloud.core.auth;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface AuthHandle {
  AuthState state();

  void onStateChanged(Consumer<AuthState> listener);

  CompletableFuture<AuthSession> result();

  void cancel();
}
