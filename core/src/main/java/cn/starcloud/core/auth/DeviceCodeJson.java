package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record DeviceCodeJson(
    @SerializedName("user_code") String userCode,
    @SerializedName("verification_uri") String verificationUri,
    @SerializedName("device_code") String deviceCode,
    @SerializedName("expires_in") int expiresIn,
    int interval,
    String message) {}
