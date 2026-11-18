package cn.starcloud.core.auth;

public sealed interface AuthState
    permits AuthState.Pending, AuthState.Success, AuthState.WaitingForUser, AuthState.Failed {
  record Pending() implements AuthState {}

  record WaitingForUser(String instruction, String url, String code) implements AuthState {}

  record Success(AuthSession session) implements AuthState {}

  record Failed(AuthFailureReason reason, Throwable cause) implements AuthState {
    public AuthFailureReason error() {
      return reason;
    }

    public Throwable cause() {
      return cause;
    }
  }
}
