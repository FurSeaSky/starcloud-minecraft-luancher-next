package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response of the XSTS authorization endpoint.
 *
 * @param token the XSTS token, exchanged for a Minecraft token
 * @param displayClaims claims block carried over from the Xbox Live token
 */
public record XstsTokenJson(
    @SerializedName("Token") String token,
    @SerializedName("DisplayClaims") XblTokenJson.DisplayClaims displayClaims) {}
