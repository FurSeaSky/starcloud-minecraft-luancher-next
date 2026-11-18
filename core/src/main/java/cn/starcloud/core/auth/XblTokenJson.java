package cn.starcloud.core.auth;

import com.google.gson.annotations.SerializedName;

public record XblTokenJson(
    @SerializedName("Token") String token,
    @SerializedName("DisplayClaims") DisplayClaims displayClaims) {
  public record DisplayClaims(@SerializedName("xui") java.util.List<Xui> xui) {
    public record Xui(@SerializedName("uhs") String uhs) {}
  }
}
