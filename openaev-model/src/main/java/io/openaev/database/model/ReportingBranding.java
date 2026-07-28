package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Branding overrides of a {@link Reporting} template, stored as a JSONB object on the reporting
 * row. Null values mean "inherit from the platform theme".
 */
@Getter
@Setter
@NoArgsConstructor
public class ReportingBranding {

  @JsonProperty("theme_mode")
  private ReportingThemeMode themeMode = ReportingThemeMode.DARK;

  @JsonProperty("background_color")
  private String backgroundColor;

  @JsonProperty("paper_color")
  private String paperColor;

  @JsonProperty("primary_color")
  private String primaryColor;

  @JsonProperty("secondary_color")
  private String secondaryColor;

  @JsonProperty("accent_color")
  private String accentColor;

  @JsonProperty("text_color")
  private String textColor;

  /** {@link Document} id overriding the platform logo; null means platform logo. */
  @JsonProperty("logo_document_id")
  private String logoDocumentId;
}
