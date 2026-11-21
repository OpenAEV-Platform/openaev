package io.openaev.database.model;

public enum SettingKeys {
  // Configuration
  DEFAULT_THEME("platform_theme", "dark"),
  DEFAULT_LANG("platform_lang", "auto"),
  PLATFORM_NAME("platform_name", "OpenAEV - Open Adversarial Exposure Validation Platform"),

  PLATFORM_CONSENT_MESSAGE("platform_consent_message", ""),
  PLATFORM_CONSENT_CONFIRM_TEXT("platform_consent_confirm_text", ""),
  PLATFORM_ENTERPRISE_LICENSE("platform_enterprise_license", ""),
  PLATFORM_LOGIN_MESSAGE(
      "platform_login_message",
      "This platform is dedicated to Filigran team testing. **Sandbox running the latest rolling release.**"),
  PLATFORM_WHITEMARK("platform_whitemark", "false"),
  PLATFORM_BANNER("platform_banner", ""),
  PLATFORM_INSTANCE("instance_id", ""),
  PLATFORM_INSTANCE_CREATION("instance_creation_date", ""),
  XTM_HUB_SHOULD_SEND_CONNECTIVITY_EMAIL("xtm_hub_should_send_connectivity_email", "true"),

  XTM_COMPOSER_ID("xtm_composer_id", ""),
  XTM_COMPOSER_NAME("xtm_composer_name", ""),
  // The version XTM Composer reports at registration and on each connectivity refresh.
  XTM_COMPOSER_VERSION("xtm_composer_version", "unknown"),
  // The container image XTM Composer reports, empty when it reports none.
  XTM_COMPOSER_IMAGE("xtm_composer_image", ""),
  // The OpenAEV version XTM Composer registered against: it registers again when it changes.
  XTM_COMPOSER_PLATFORM_VERSION("xtm_composer_platform_version", ""),
  XTM_COMPOSER_PUBLIC_KEY("xtm_composer_public_key", ""),
  XTM_COMPOSER_LAST_CONNECTIVITY_CHECK("xtm_composer_last_connectivity_check", ""),

  SMTP_SERVICE_AVAILABLE("smtp_service_available", "false"),
  IMAP_SERVICE_AVAILABLE("imap_service_available", "false"),

  // Session management
  PLATFORM_SESSION_MAX_CONCURRENT("platform_session_max_concurrent", "0"),

  // Inject execution: minutes before a pending inject is finalized with a timeout. No default
  // here: while unset, inject.execution.threshold.minutes applies
  // (InjectExecutionThresholdService).
  INJECT_EXECUTION_THRESHOLD_MINUTES("inject_execution_threshold_minutes", ""),

  // AI capabilities
  FILIGRAN_CHATBOT_AI_CGU_STATUS("filigran_chatbot_ai_cgu_status", ""),

  USER_EVENTS_RETENTION_ENABLED("user-events.retention.enabled", "true"),
  USER_EVENTS_RETENTION_DEFAULT_DAYS("user-events.retention.default-days", "90"),
  USER_EVENTS_RETENTION_LOGIN_SUCCESS_DAYS("user-events.retention.login-success.days", "90"),
  USER_EVENTS_RETENTION_LOGIN_FAILED_DAYS("user-events.retention.login-failed.days", "90"),
  USER_EVENTS_RETENTION_USER_CREATED_DAYS("user-events.retention.user-created.days", "90");

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
