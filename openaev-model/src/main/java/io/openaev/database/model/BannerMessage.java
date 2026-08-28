package io.openaev.database.model;

public class BannerMessage {

  public enum BANNER_KEYS {
    CALDERA_UNAVAILABLE(
        "caldera_unavailable",
        BANNER_LEVEL.ERROR,
        "The Caldera executor is not responding, your simulations may be impacted."),
    SAFE_MODE_ENABLED(
        "safe_mode_enabled",
        BANNER_LEVEL.WARN,
        "Safe mode is active: background processing is disabled."),
    AUDIT_LOG_NO_ENTERPRISE_LICENSE(
        "audit_log_no_enterprise_license",
        BANNER_LEVEL.WARN,
        "Audit logging is inactive: an Enterprise Edition license is required to enable this feature.");

    private final String key;
    private final BANNER_LEVEL level;
    private final String message;

    BANNER_KEYS(String key, BANNER_LEVEL level, String message) {
      this.key = key;
      this.level = level;
      this.message = message;
    }

    public String key() {
      return key;
    }

    public String message() {
      return message;
    }

    public BANNER_LEVEL level() {
      return level;
    }
  }

  public enum BANNER_LEVEL {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
  }
}
