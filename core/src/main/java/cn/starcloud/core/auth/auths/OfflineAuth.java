package cn.starcloud.core.auth.auths;

import cn.starcloud.core.auth.dto.AuthSession;
import cn.starcloud.core.auth.enums.AuthFailureReason;
import cn.starcloud.core.auth.exception.AuthException;
import cn.starcloud.core.auth.itfes.AuthHandle;
import cn.starcloud.core.auth.itfes.AuthState;
import cn.starcloud.core.auth.itfes.IAuth;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Offline login: no provider round trip, identity derived from the username alone.
 *
 * <p>The UUID follows the vanilla {@code OfflinePlayer:<name>} rule so worlds and servers recognise
 * the account. Username rules mirror vanilla too: 2-16 characters from {@code a-z}, {@code A-Z},
 * {@code 0-9}, {@code _} and {@code -}.
 */
public class OfflineAuth implements IAuth {
  private String username;
  private String uuid;

  /** Creates an instance that still needs a username before {@link #login()} can succeed. */
  public OfflineAuth() {}

  /**
   * Sets the name of the account to log in with.
   *
   * @param username name to validate on the next {@link #login()}
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * {@inheritDoc}
   *
   * @return always {@code null}: offline sessions never expire, so there is nothing to renew
   */
  @Override
  public AuthSession refresh(String refreshToken) throws AuthException {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>No-op: an offline account has no server-side session.
   */
  @Override
  public void logout(String accessToken) throws AuthException {}

  /**
   * Validates the configured username and produces a session immediately.
   *
   * <p>Synchronous: the returned handle is already terminal when it comes back.
   *
   * @return handle holding either a {@link AuthState.Success} with a non-expiring session or a
   *     {@link AuthState.Failed} carrying {@link AuthFailureReason#INVALID_USERNAME_LENGTH} or
   *     {@link AuthFailureReason#INVALID_USERNAME_CHARSET}
   */
  @Override
  public AuthHandle login() {
    DefaultAuthHandle handle = new DefaultAuthHandle();

    if (username == null || username.isBlank()) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_USERNAME_LENGTH,
              new AuthException("Offline auth requires a non-blank username")));
      return handle;
    }

    if (username.length() < 2 || username.length() > 16) {
      handle.update(
          new AuthState.Failed(
              AuthFailureReason.INVALID_USERNAME_LENGTH,
              new AuthException("Offline username length must be between 2 and 16")));
      return handle;
    }

    for (char c : username.toCharArray()) {
      boolean valid =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '_'
              || c == '-';
      if (!valid) {
        handle.update(
            new AuthState.Failed(
                AuthFailureReason.INVALID_USERNAME_CHARSET,
                new AuthException("Offline username only allows a-z, A-Z, 0-9, _ and -")));
        return handle;
      }
    }

    String uuid =
        UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
            .toString();
    this.uuid = uuid;
    AuthSession session = new AuthSession(username, uuid, "0", "0", Instant.MAX);
    handle.update(new AuthState.Success(session));
    return handle;
  }
}
