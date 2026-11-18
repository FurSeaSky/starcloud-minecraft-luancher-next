package cn.starcloud.core.auth.auths;

import cn.starcloud.core.auth.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public class OfflineAuth implements IAuth {
  private String username;
  private String uuid;

  public void setUsername(String username) {
    this.username = username;
  }

  @Override
  public AuthSession refresh(String refreshToken) throws AuthException {
    return null;
  }

  @Override
  public void logout(String accessToken) throws AuthException {}

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
