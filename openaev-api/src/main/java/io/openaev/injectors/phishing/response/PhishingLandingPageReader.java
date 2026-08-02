package io.openaev.injectors.phishing.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.PhishingLandingPage;
import lombok.Getter;

/**
 * Public, credential-free view of a landing page served to an unauthenticated victim browser. Only
 * the fields required to render the page are exposed - never capture settings that would help an
 * attacker, nor tenant/owner metadata.
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

  @JsonProperty("phishing_capture_submitted_data")
  private final boolean captureSubmittedData;

  @JsonProperty("phishing_capture_passwords")
  private final boolean capturePasswords;

  public PhishingLandingPageReader(final PhishingLandingPage landingPage) {
    this.name = landingPage.getName();
    this.html = landingPage.getHtml();
    this.css = landingPage.getCss();
    this.primaryColorDark = landingPage.getPrimaryColorDark();
    this.primaryColorLight = landingPage.getPrimaryColorLight();
    this.logoDark = landingPage.getLogoDark() != null ? landingPage.getLogoDark().getId() : null;
    this.logoLight = landingPage.getLogoLight() != null ? landingPage.getLogoLight().getId() : null;
    this.captureSubmittedData = landingPage.isCaptureSubmittedData();
    this.capturePasswords = landingPage.isCapturePasswords();
  }
}
