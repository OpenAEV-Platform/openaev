package io.openaev.database.model;

public enum TenantSettingKeys {
  // Configuration
  PLATFORM_NAME("platform_name", "OpenAEV - Open Adversarial Exposure Validation Platform", true),
  DEFAULT_THEME("platform_theme", "dark", true),
  DEFAULT_LANG("platform_lang", "auto", true),
  // Dashboards
  TENANT_HOME_DASHBOARD("platform_home_dashboard", "", false),
  TENANT_SCENARIO_DASHBOARD("platform_scenario_dashboard", "", false),
  TENANT_SIMULATION_DASHBOARD("platform_simulation_dashboard", "", false),
  // Autonomous attack: JSON array of XTM One agent ids the orchestrator consults by default
  AUTONOMOUS_ADDITIONAL_AGENTS("platform_autonomous_additional_agents", "", false),
  // Autonomous attack: JSON object mapping an agent id to its default discovery mode
  // (EXISTING_ONLY / SCOPED / EXPANSIVE) - how much latitude the agent has to create new
  // assets / findings / persons from recon on the fly.
  AUTONOMOUS_ADDITIONAL_AGENT_MODES("platform_autonomous_additional_agent_modes", "", false),
  // IOC validation safety settings (OpenCTI dissemination assurance). Comma-separated test kinds;
  // only DNS resolution is allowed until an administrator widens the list.
  IOC_VALIDATION_ALLOWED_TEST_KINDS("ioc_validation_allowed_test_kinds", "DNS_RESOLUTION", false),
  IOC_VALIDATION_HTTP_PROXY_URL("ioc_validation_http_proxy_url", "", false),
  IOC_VALIDATION_SINKHOLE_ADDRESS("ioc_validation_sinkhole_address", "", false),
  IOC_VALIDATION_NETWORK_PORT("ioc_validation_network_port", "443", false),
  IOC_VALIDATION_ASSET_GROUP("ioc_validation_asset_group", "", false);

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
