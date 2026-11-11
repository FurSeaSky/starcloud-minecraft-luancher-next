package cn.starcloud.core.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class OfflineUuid {

  private OfflineUuid() {}

  public static String forUsername(String username) {
    validate(username);
    return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static void validate(String username) {
    if (username == null || username.length() < 2 || username.length() > 16) {
      throw new IllegalArgumentException("Username must be 2-16 characters");
    }
    for (int i = 0; i < username.length(); i++) {
      char c = username.charAt(i);
      boolean valid =
          (c >= 'a' && c <= 'z')
              || (c >= 'A' && c <= 'Z')
              || (c >= '0' && c <= '9')
              || c == '_'
              || c == '-';
      if (!valid) {
        throw new IllegalArgumentException("Invalid character in username: " + c);
      }
    }
  }
}
