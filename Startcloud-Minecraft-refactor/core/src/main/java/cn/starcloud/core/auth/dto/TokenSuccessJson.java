package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Successful response of the OAuth token endpoint.
 *
 * @param accessToken token used to obtain the next token in the chain
 * @param refreshToken token used to renew this session later
 * @param expiresIn lifetime of the access token in seconds
 * @param tokenType scheme to use in the {@code Authorization} header
 */
public record TokenSuccessJson(
    @SerializedName("access_token") String accessToken,
    @SerializedName("refresh_token") String refreshToken,
    @SerializedName("expires_in") int expiresIn,
    @SerializedName("token_type") String tokenType) {}
