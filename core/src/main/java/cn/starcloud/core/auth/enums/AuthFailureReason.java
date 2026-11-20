package cn.starcloud.core.auth.enums;

/**
 * Machine-readable failure reason of a login attempt.
 *
 * <p>Deliberately free of user-facing text: the core reports what happened, the caller decides how
 * to word it and in which language.
 */
public enum AuthFailureReason {
  /** Username is blank or outside the 2-16 character range. */
  INVALID_USERNAME_LENGTH,
  /**
   * Username contains characters outside {@code a-z}, {@code A-Z}, {@code 0-9}, {@code _} and
   * {@code -}.
   */
  INVALID_USERNAME_CHARSET,
  /** The provider could not be reached or the connection broke mid-flow. */
  NETWORK_ERROR,
  /** The user did not finish the flow before the device code expired. */
  DEVICE_CODE_EXPIRED,
  /** The provider rejected the credentials or returned a body that could not be parsed. */
  INVALID_CREDENTIALS,
  /** The flow was cancelled, by the user or by an interrupt. */
  CANCELLED,
  /** The client id is missing or not accepted by Microsoft. */
  INVALID_CLIENT_ID,
  /** Any failure that does not map to a more specific reason. */
  UNKNOWN
}
