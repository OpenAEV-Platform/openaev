package io.openaev.database.model;

public enum SettingKeys {
  DEFAULT_THEME("platform_theme", "dark"),
  DEFAULT_LANG("platform_lang", "auto"),
  DEFAULT_HOME_DASHBOARD("platform_home_dashboard", ""),
  DEFAULT_SCENARIO_DASHBOARD("platform_scenario_dashboard", ""),
  DEFAULT_SIMULATION_DASHBOARD("platform_simulation_dashboard", ""),
  PLATFORM_CONSENT_MESSAGE("platform_consent_message", ""),
  PLATFORM_CONSENT_CONFIRM_TEXT("platform_consent_confirm_text", ""),
  PLATFORM_ENTERPRISE_LICENSE("platform_enterprise_license", ""),
  PLATFORM_LOGIN_MESSAGE(
      "platform_login_message",
      "This platform is dedicated to Filigran team testing. **Sandbox running the latest rolling release.**"),
  PLATFORM_WHITEMARK("platform_whitemark", "false"),
  PLATFORM_NAME("platform_name", "OpenAEV - Open Adversarial Exposure Validation Platform"),
  PLATFORM_BANNER("platform_banner", ""),
  PLATFORM_INSTANCE("instance_id", ""),
  PLATFORM_INSTANCE_CREATION("instance_creation_date", ""),
  XTM_HUB_TOKEN("xtm_hub_token", ""),
  XTM_HUB_REGISTRATION_DATE("xtm_hub_registration_date", ""),
  XTM_HUB_REGISTRATION_STATUS("xtm_hub_registration_status", ""),
  XTM_HUB_REGISTRATION_USER_ID("xtm_hub_registration_user_id", ""),
  XTM_HUB_REGISTRATION_USER_NAME("xtm_hub_registration_user_name", ""),
  XTM_HUB_LAST_CONNECTIVITY_CHECK("xtm_hub_last_connectivity_check", ""),
  XTM_HUB_SHOULD_SEND_CONNECTIVITY_EMAIL("xtm_hub_should_send_connectivity_email", "true"),

  XTM_COMPOSER_ID("xtm_composer_id", ""),
  XTM_COMPOSER_NAME("xtm_composer_name", ""),
  XTM_COMPOSER_VERSION("xtm_composer_version", ""),
  XTM_COMPOSER_PUBLIC_KEY("xtm_composer_public_key", ""),
  XTM_COMPOSER_LAST_CONNECTIVITY_CHECK("xtm_composer_last_connectivity_check", ""),

  SMTP_SERVICE_AVAILABLE("smtp_service_available", "false"),
  IMAP_SERVICE_AVAILABLE("imap_service_available", "false");

  private final String key;
  private final String defaultValue;

  SettingKeys(String key, String defaultValue) {
    this.key = key;
    this.defaultValue = defaultValue;
  }

  public String key() {
    return key;
  }

  public String defaultValue() {
    return defaultValue;
  }
}
