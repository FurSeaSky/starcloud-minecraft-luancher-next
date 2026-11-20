package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Minecraft profile of the signed-in account.
 *
 * @param id the account UUID without dashes, as returned by the profile endpoint
 * @param name the in-game name
 */
public record MinecraftProfileJson(
    @SerializedName("id") String id, @SerializedName("name") String name) {}
