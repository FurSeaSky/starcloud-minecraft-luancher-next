package cn.starcloud.core.auth.enums;

/**
 * Authentication method used by an account.
 *
 * <p>Persisted by callers, so keep the constant names stable.
 */
public enum AuthType {
  /** Microsoft account, authenticated through the device code flow. */
  MSA,
  /** Offline account: no provider, UUID derived from the name. */
  OFFLINE,
  /** Third-party authentication server. */
  TRD;
}
