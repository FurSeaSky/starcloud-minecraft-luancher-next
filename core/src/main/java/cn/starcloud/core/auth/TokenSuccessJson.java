package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record TokenSuccessJson(
    @SerializedName("access_token") String accessToken,
    @SerializedName("refresh_token") String refreshToken,
    @SerializedName("expires_in") int expiresIn,
    @SerializedName("token_type") String tokenType) {}
