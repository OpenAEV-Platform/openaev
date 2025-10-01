package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Data
public class Theme implements Base {

  public enum THEME_KEYS {
    BACKGROUND_COLOR("background_color", ""),
    PAPER_COLOR("paper_color", ""),
    NAVIGATION_COLOR("navigation_color", ""),
    PRIMARY_COLOR("primary_color", ""),
    SECONDARY_COLOR("secondary_color", ""),
    ACCENT_COLOR("accent_color", ""),
    LOGO_URL("logo_url", ""),
    LOGO_URL_COLLAPSED("logo_url_collapsed", ""),
    LOGO_LOGIN_URL("logo_login_url", "");

    private final String key;
    private final String defaultValue;

    THEME_KEYS(String key, String defaultValue) {
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

  public Theme() {
    // Default constructor
  }

  @Id
  @Column(name = "personalized_theme_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("theme_id")
  private String id;

  @Column(name = "personalized_theme_key")
  @JsonProperty("theme_key")
  private String key;

  @Column(name = "personalized_theme_value")
  @JsonProperty("theme_value")
  private String value;

  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }
}
