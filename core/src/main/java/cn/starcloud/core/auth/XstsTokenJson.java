package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record XstsTokenJson(
    @SerializedName("Token") String token,
    @SerializedName("DisplayClaims") XblTokenJson.DisplayClaims displayClaims) {}
