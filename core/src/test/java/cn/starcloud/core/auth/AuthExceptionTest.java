package cn.starcloud.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class AuthExceptionTest {

  @Test
  void keepsMessage() {
    assertEquals("boom", new AuthException("boom").getMessage());
  }

  @Test
  void keepsMessageAndCause() {
    IllegalStateException cause = new IllegalStateException("token expired");

    AuthException exception = new AuthException("login failed", cause);

    assertEquals("login failed", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void hasNoCauseWhenNotSupplied() {
    assertNull(new AuthException("no cause").getCause());
  }
}
