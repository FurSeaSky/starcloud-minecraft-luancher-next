package cn.starcloud.core.launcherCore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class InitExceptionTest {

  @Test
  void keepsMessage() {
    assertEquals("boom", new InitException("boom").getMessage());
  }

  @Test
  void keepsMessageAndCause() {
    IOException cause = new IOException("disk full");

    InitException exception = new InitException("init failed", cause);

    assertEquals("init failed", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void hasNoCauseWhenNotSupplied() {
    assertNull(new InitException("no cause").getCause());
  }
}
