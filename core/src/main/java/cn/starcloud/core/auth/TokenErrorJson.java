package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record TokenErrorJson(
    @SerializedName("error") String error,
    @SerializedName("error_description") String errorDescription) {}
