package cn.starcloud.core.auth;

public interface IAuth {
  AuthSession refresh(String refreshToken) throws AuthException;

  void logout(String accessToken) throws AuthException;

  AuthHandle login();
}
