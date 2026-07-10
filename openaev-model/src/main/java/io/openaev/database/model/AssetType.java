package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AssetType {
  @JsonProperty("asset_type_endpoint")
  Endpoint(Values.ENDPOINT_TYPE),
  @JsonProperty("asset_type_security_platform")
  SecurityPlatform(Values.SECURITY_PLATFORM_TYPE),
  @JsonProperty("asset_type_ai_target")
  AiTarget(Values.AI_TARGET_TYPE);

  public final String value;

  AssetType(String value) {
    this.value = value;
  }

  public static class Values {
    public static final String ENDPOINT_TYPE = "Endpoint";
    public static final String SECURITY_PLATFORM_TYPE = "SecurityPlatform";
    public static final String AI_TARGET_TYPE = "AiTarget";
  }
}
