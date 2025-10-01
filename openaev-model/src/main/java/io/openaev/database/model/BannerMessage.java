package io.openaev.database.model;

public class BannerMessage {

  public enum BANNER_KEYS {
    IMAP_UNAVAILABLE(
        "imap_unavailable",
        BANNER_LEVEL.ERROR,
        "IMAP service is not responding, your injectors may be impacted."),
    CALDERA_UNAVAILABLE(
        "caldera_unavailable",
        BANNER_LEVEL.ERROR,
        "The Caldera executor is not responding, your simulations may be impacted.");

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
