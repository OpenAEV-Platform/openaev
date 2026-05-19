package io.openaev.database.model;

public enum TenantSettingKeys {
  // Configuration
  PLATFORM_NAME("platform_name", "OpenAEV - Open Adversarial Exposure Validation Platform", true),
  DEFAULT_THEME("platform_theme", "dark", true),
  DEFAULT_LANG("platform_lang", "auto", true),
  // Dashboards
  TENANT_HOME_DASHBOARD("platform_home_dashboard", "", false),
  TENANT_SCENARIO_DASHBOARD("platform_scenario_dashboard", "", false),
  TENANT_SIMULATION_DASHBOARD("platform_simulation_dashboard", "", false);

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
