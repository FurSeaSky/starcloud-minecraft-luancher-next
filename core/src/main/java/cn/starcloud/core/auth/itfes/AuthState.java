package cn.starcloud.core.auth.itfes;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.enums.AuthFailureReason;

/**
 * State of a single login attempt.
 *
 * <p>Sealed on purpose: every {@code switch} over this type is exhaustive at compile time, so
 * adding a state breaks the build instead of falling through to a default branch at runtime.
 */
public sealed interface AuthState
    permits AuthState.Pending, AuthState.Success, AuthState.WaitingForUser, AuthState.Failed {

  /** The attempt was created and has not produced anything the user can act on yet. */
  record Pending() implements AuthState {}

  /**
   * The user has to finish the login out of band, for example in a browser.
   *
   * @param instruction human-readable instructions from the identity provider
   * @param url page the user must open
   * @param code code the user must enter on that page
   */
  record WaitingForUser(String instruction, String url, String code) implements AuthState {}

  /**
   * The attempt succeeded.
   *
   * @param session the resulting session
   */
  record Success(AuthSession session) implements AuthState {}

  /**
   * The attempt failed through the provider, the network, or cancellation.
   *
   * @param reason machine-readable reason, meant to be translated by the caller
   * @param cause underlying error, {@code null} when no exception applies
   */
  record Failed(AuthFailureReason reason, Throwable cause) implements AuthState {

    /**
     * Returns the failure reason.
     *
     * @return the reason, same value as {@link #reason()}
     */
    public AuthFailureReason error() {
      return reason;
    }

    /**
     * Returns the underlying error.
     *
     * @return the cause, may be {@code null}
     */
    public Throwable cause() {
      return cause;
    }
  }
}
