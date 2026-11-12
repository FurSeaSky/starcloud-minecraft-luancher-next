package cn.starcloud.core.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OfflineUuidTest {

  @Test
  void generatesStandardUuidForKnownUsername() {
    assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", OfflineUuid.forUsername("Notch"));
  }

  @Test
  void isDeterministic() {
    assertEquals(OfflineUuid.forUsername("Steve"), OfflineUuid.forUsername("Steve"));
  }

  @Test
  void isCaseSensitive() {
    assertNotEquals(OfflineUuid.forUsername("Steve"), OfflineUuid.forUsername("steve"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a", "", "  ", "this_name_is_way_too_long_for_minecraft"})
  void rejectsInvalidUsernames(String username) {
    assertThrows(IllegalArgumentException.class, () -> OfflineUuid.forUsername(username));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Notch", "jeb_", "Player123", "a_b-c"})
  void acceptsValidUsernames(String username) {
    assertDoesNotThrow(() -> OfflineUuid.forUsername(username));
  }
}
