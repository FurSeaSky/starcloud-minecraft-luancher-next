package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response of the Minecraft login endpoint.
 *
 * @param accessToken token to present to Minecraft services
 * @param expiresIn lifetime of the token in seconds
 */
public record MinecraftTokenJson(
    @SerializedName("access_token") String accessToken,
    @SerializedName("expires_in") int expiresIn) {}
