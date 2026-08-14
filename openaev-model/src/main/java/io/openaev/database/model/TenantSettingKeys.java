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
  // Findings: number of days of inactivity (no re-detection) after which a finding is
  // considered archived on the Finding page. Configurable per-tenant from the Finding page.
  FINDING_ARCHIVE_DAYS("finding_archive_days", "30", false);

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
