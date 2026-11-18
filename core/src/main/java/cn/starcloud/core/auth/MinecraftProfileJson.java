package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record MinecraftProfileJson(
    @SerializedName("id") String id, @SerializedName("name") String name) {}
