package io.openaev.database.model;

public enum TenantSettingKeys {
  TENANT_HOME_DASHBOARD("home_dashboard", "", false),
  TENANT_SCENARIO_DASHBOARD("scenario_dashboard", "", false),
  TENANT_SIMULATION_DASHBOARD("simulation_dashboard", "", false);

  private final String key;
  private final String defaultValue;
  private final boolean platformFallback;

  TenantSettingKeys(String key, String defaultValue, boolean platformFallback) {
    this.key = key;
    this.defaultValue = defaultValue;
    this.platformFallback = platformFallback;
  }

  public String key() {
    return key;
  }

  public String defaultValue() {
    return defaultValue;
  }

  public boolean hasPlatformFallback() {
    return platformFallback;
  }
}
