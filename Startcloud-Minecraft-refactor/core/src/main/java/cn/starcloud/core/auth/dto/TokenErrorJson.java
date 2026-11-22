package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Error body returned by the token endpoints.
 *
 * @param error stable machine-readable code, for example {@code expired_token}
 * @param errorDescription human-readable explanation; may be absent
 */
public record TokenErrorJson(
    @SerializedName("error") String error,
    @SerializedName("error_description") String errorDescription) {}
