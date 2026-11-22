package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response of the device code request that starts a Microsoft login.
 *
 * @param userCode short code the user types on the verification page
 * @param verificationUri page the user must open
 * @param deviceCode opaque code used to poll for the token; never show it to the user
 * @param expiresIn lifetime of this device code in seconds
 * @param interval minimum seconds between two polls
 * @param message ready-made instructions returned by Microsoft, if present
 */
public record DeviceCodeJson(
    @SerializedName("user_code") String userCode,
    @SerializedName("verification_uri") String verificationUri,
    @SerializedName("device_code") String deviceCode,
    @SerializedName("expires_in") int expiresIn,
    int interval,
    String message) {}
