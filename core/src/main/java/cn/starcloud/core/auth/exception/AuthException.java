package cn.starcloud.core.auth.exception;

/**
 * Thrown when a login, refresh, or logout cannot be completed.
 *
 * <p>The message is the {@link cn.starcloud.core.auth.enums.AuthFailureReason#name() reason name}
 * when the failure came from an {@link cn.starcloud.core.auth.itfes.AuthState.Failed} state, so
 * callers can map it back to an enum without parsing prose.
 */
public class AuthException extends RuntimeException {

  /**
   * Creates the exception with a message.
   *
   * @param message failure description, usually a reason name
   */
  public AuthException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a cause.
   *
   * @param cause the underlying failure
   */
  public AuthException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates the exception with a message and a cause.
   *
   * @param message failure description, usually a reason name
   * @param cause the underlying failure
   */
  public AuthException(String message, Throwable cause) {
    super(message, cause);
  }
}
