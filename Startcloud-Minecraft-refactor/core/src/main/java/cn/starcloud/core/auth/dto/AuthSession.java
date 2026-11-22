package cn.starcloud.core.auth.dto;

import java.time.Instant;

/**
 * Result of a successful login.
 *
 * @param username display name shown in game
 * @param uuid account UUID; for offline accounts it is derived from the name, not fetched
 * @param accessToken token attached to game and profile requests
 * @param refreshToken token used to renew the session; {@code "0"} for offline accounts
 * @param expitesAt instant the access token expires, {@link Instant#MAX} for offline accounts
 */
public record AuthSession(
    String username, String uuid, String accessToken, String refreshToken, Instant expitesAt) {}
