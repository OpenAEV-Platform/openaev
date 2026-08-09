package io.openaev.injectors.phishing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/** Logo (dark/light) binding payload for a phishing landing page. */
@Getter
@Setter
public class PhishingLandingPageLogoInput {

  @JsonProperty("phishing_landing_page_logo_dark")
  private String logoDark;

  @JsonProperty("phishing_landing_page_logo_light")
  private String logoLight;
}
