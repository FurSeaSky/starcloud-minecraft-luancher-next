package cn.starcloud.core.auth.enums;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * The enum names cross module and process boundaries, so their spelling is part of the contract.
 */
class AuthEnumsTest {

  @Test
  void authTypeNamesStayStable() {
    assertArrayEquals(
        new String[] {"MSA", "OFFLINE", "TRD"},
        new String[] {AuthType.MSA.name(), AuthType.OFFLINE.name(), AuthType.TRD.name()});
  }

  @Test
  void failureReasonNamesStayStable() {
    assertArrayEquals(
        new String[] {
          "INVALID_USERNAME_LENGTH",
          "INVALID_USERNAME_CHARSET",
          "NETWORK_ERROR",
          "DEVICE_CODE_EXPIRED",
          "INVALID_CREDENTIALS",
          "CANCELLED",
          "INVALID_CLIENT_ID",
          "UNKNOWN"
        },
        new String[] {
          AuthFailureReason.INVALID_USERNAME_LENGTH.name(),
          AuthFailureReason.INVALID_USERNAME_CHARSET.name(),
          AuthFailureReason.NETWORK_ERROR.name(),
          AuthFailureReason.DEVICE_CODE_EXPIRED.name(),
          AuthFailureReason.INVALID_CREDENTIALS.name(),
          AuthFailureReason.CANCELLED.name(),
          AuthFailureReason.INVALID_CLIENT_ID.name(),
          AuthFailureReason.UNKNOWN.name()
        });
  }
}
