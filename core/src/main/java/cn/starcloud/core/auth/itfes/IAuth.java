package cn.starcloud.core.auth.itfes;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.exception.AuthException;

/**
 * Contract shared by every authentication method.
 *
 * <p>Only the session operations are shared. Login is not: offline login is a single synchronous
 * step, Microsoft login is a two-step device code flow, and both differ in what they need from the
 * caller. Each implementation therefore exposes its own entry point and returns an {@link
 * AuthHandle} for progress.
 */
public interface IAuth {

  /**
   * Renews a session from a refresh token.
   *
   * @param refreshToken token issued for the earlier session
   * @return the renewed session, or {@code null} for providers whose sessions never expire
   * @throws AuthException when the token is rejected or the provider cannot be reached
   */
  AuthSession refresh(String refreshToken) throws AuthException;

  /**
   * Invalidates the session at the provider.
   *
   * @param accessToken token of the session to revoke
   * @throws AuthException when revocation fails
   */
  void logout(String accessToken) throws AuthException;

  /**
   * Starts a login attempt without blocking the caller.
   *
   * @return handle that reports progress and carries the result
   */
  AuthHandle login();
}
