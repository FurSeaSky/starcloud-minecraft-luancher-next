package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record MinecraftTokenJson(
    @SerializedName("access_token") String accessToken,
    @SerializedName("expires_in") int expiresIn) {}
