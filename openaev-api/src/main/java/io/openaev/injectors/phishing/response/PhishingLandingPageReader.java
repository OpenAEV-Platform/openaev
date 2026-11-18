package io.openaev.injectors.phishing.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.PhishingLandingPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * Public, credential-free view of a landing page served to an unauthenticated victim browser. Only
 * the fields required to render the page and confirm an interaction are exposed - never capture
 * settings that would help an attacker, nor tenant/owner metadata.
 */
@Getter
public class PhishingLandingPageReader {

  @JsonProperty("phishing_landing_page_name")
  private final String name;

  @JsonProperty("phishing_landing_page_html")
  private final String html;

  @JsonProperty("phishing_landing_page_css")
  private final String css;

  @JsonProperty("phishing_landing_page_primary_color_dark")
  private final String primaryColorDark;

  @JsonProperty("phishing_landing_page_primary_color_light")
  private final String primaryColorLight;

  @JsonProperty("phishing_landing_page_logo_dark")
  private final String logoDark;

  @JsonProperty("phishing_landing_page_logo_light")
  private final String logoLight;

  /**
   * Minimum time, in milliseconds, the page waits after rendering before it reports an interaction
   * to the confirmation endpoint. The endpoint applies the same value, so the page reads it here
   * instead of duplicating it. Nothing is disclosed: the page applies it in the visitor's browser.
   */
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("phishing_landing_page_min_dwell_ms")
  private final long minDwellMs;

  public PhishingLandingPageReader(final PhishingLandingPage landingPage, final long minDwellMs) {
    this.minDwellMs = minDwellMs;
    this.name = landingPage.getName();
    this.html = landingPage.getHtml();
    this.css = landingPage.getCss();
    this.primaryColorDark = landingPage.getPrimaryColorDark();
    this.primaryColorLight = landingPage.getPrimaryColorLight();
    this.logoDark = landingPage.getLogoDark() != null ? landingPage.getLogoDark().getId() : null;
    this.logoLight = landingPage.getLogoLight() != null ? landingPage.getLogoLight().getId() : null;
  }
}
