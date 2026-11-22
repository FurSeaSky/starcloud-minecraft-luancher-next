package cn.starcloud.core.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import cn.starcloud.core.auth.exception.AuthException;
import org.junit.jupiter.api.Test;

/**
 * The exception is built from several shapes; each must keep the message and cause it was given so
 * callers can still map failures back to their origin.
 */
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
