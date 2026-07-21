package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AssetType {
  @JsonProperty("asset_type_asset")
  Asset(Values.ASSET_TYPE),
  @JsonProperty("asset_type_endpoint")
  Endpoint(Values.ENDPOINT_TYPE),
  @JsonProperty("asset_type_security_platform")
  SecurityPlatform(Values.SECURITY_PLATFORM_TYPE);

  public final String value;

  AssetType(String value) {
    this.value = value;
  }

  public static class Values {
    /**
     * Discriminator for the concrete {@code Asset} base - every non-agentic target category (cloud,
     * web, network, IoT, SaaS, identity, generic) and AI targets ({@code category = AI_TARGET})
     * persist with this type.
     */
    public static final String ASSET_TYPE = "Asset";

    public static final String ENDPOINT_TYPE = "Endpoint";
    public static final String SECURITY_PLATFORM_TYPE = "SecurityPlatform";
  }
}
