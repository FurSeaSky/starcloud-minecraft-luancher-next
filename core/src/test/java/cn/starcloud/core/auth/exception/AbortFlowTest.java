package cn.starcloud.core.auth.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * The signal is rethrown through public seams, so it must carry whatever message and cause the
 * aborting step attached.
 */
class AbortFlowTest {

  @Test
  void keepsMessage() {
    assertEquals("abort", new AbortFlow("abort").getMessage());
  }

  @Test
  void keepsMessageAndCause() {
    IOException cause = new IOException("connection reset");

    AbortFlow abort = new AbortFlow("abort", cause);

    assertEquals("abort", abort.getMessage());
    assertSame(cause, abort.getCause());
  }

  @Test
  void hasNoMessageOrCauseByDefault() {
    AbortFlow abort = new AbortFlow();

    assertNull(abort.getMessage());
    assertNull(abort.getCause());
  }
}
