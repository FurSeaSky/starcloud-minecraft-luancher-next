package cn.starcloud.core.auth;

import java.time.Instant;

public record AuthSession(
    String username, String uuid, String accessToken, String refreshToken, Instant expitesAt) {}
