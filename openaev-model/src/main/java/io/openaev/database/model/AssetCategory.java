package io.openaev.database.model;

/**
 * Product-facing primary classification of an {@link Asset} (level 1 of the asset taxonomy).
 *
 * <p>This is decoupled from the JPA discriminator {@code asset_type} (Asset / Endpoint /
 * SecurityPlatform), which remains an internal persistence concern. The category is what the user
 * actually sees and what drives the "smart" creation forms, the unified inventory facets and the
 * asset-group dynamic filters. Each category maps to a security domain so that targets line up with
 * the attacks run against them (see {@code PresetDomain}).
 */
public enum AssetCategory {
  HOST("Endpoint"),
  CONTAINER_WORKLOAD("Endpoint"),
  CLOUD_RESOURCE("Cloud"),
  WEB_APPLICATION("Web App"),
  NETWORK_DEVICE("Network"),
  MOBILE_DEVICE("Endpoint"),
  IOT_OT_DEVICE("Network"),
  IDENTITY("Identity"),
  SAAS_APPLICATION("Cloud"),
  AI_TARGET("Artificial Intelligence"),
  SECURITY_PLATFORM("Endpoint"),
  GENERIC_ASSET("To classify");

  /** Name of the preset security domain this category is aligned with. */
  private final String domain;

  AssetCategory(String domain) {
    this.domain = domain;
  }

  public String getDomain() {
    return this.domain;
  }
}
