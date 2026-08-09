package io.openaev.injectors.phishing.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Create/update payload for a reusable phishing landing page. */
@Getter
@Setter
public class PhishingLandingPageInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phishing_landing_page_name")
  private String name;

  @JsonProperty("phishing_landing_page_description")
  private String description;

  @JsonProperty("phishing_landing_page_html")
  private String html;

  @JsonProperty("phishing_landing_page_css")
  private String css;

  @JsonProperty("phishing_landing_page_capture_submitted_data")
  private boolean captureSubmittedData = true;

  @JsonProperty("phishing_landing_page_capture_passwords")
  private boolean capturePasswords = true;

  @JsonProperty("phishing_landing_page_redirect_url")
  private String redirectUrl;

  @JsonProperty("phishing_landing_page_primary_color_dark")
  private String primaryColorDark;

  @JsonProperty("phishing_landing_page_primary_color_light")
  private String primaryColorLight;

  /** Optional verified custom domain to serve this page on; null uses the platform domain. */
  @JsonProperty("phishing_landing_page_custom_domain")
  private String customDomainId;
}
