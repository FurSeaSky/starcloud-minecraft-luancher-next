package cn.starcloud.core.auth.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Response of the Xbox Live authentication endpoint.
 *
 * @param token the Xbox Live token
 * @param displayClaims claims bound to the token, including the user hash needed for the next step
 */
public record XblTokenJson(
    @SerializedName("Token") String token,
    @SerializedName("DisplayClaims") DisplayClaims displayClaims) {

  /**
   * Claims block of an Xbox Live token.
   *
   * @param xui one entry per authenticated user
   */
  public record DisplayClaims(@SerializedName("xui") java.util.List<Xui> xui) {

    /**
     * Single user's claim set.
     *
     * @param uhs user hash, passed to the XSTS endpoint as {@code Userhash}
     */
    public record Xui(@SerializedName("uhs") String uhs) {}
  }
}
