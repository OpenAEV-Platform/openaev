package io.openaev.injectors.phishing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Browser-side signals sent by the public landing page once the visitor interacted with it (mouse,
 * touch, keyboard or scroll). {@code dwellMs} is the time between the page being rendered and that
 * first interaction; {@code webdriver} mirrors {@code navigator.webdriver}.
 */
@Getter
@Setter
public class PhishingConfirmInput {

  @JsonProperty("dwell_ms")
  private long dwellMs;

  @JsonProperty("webdriver")
  private boolean webdriver;
}
