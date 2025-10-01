package io.openaev.rest.settings.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PolicyInput {

  @JsonProperty("platform_login_message")
  @Schema(description = "Message to show at login")
  private String loginMessage;

  @JsonProperty("platform_consent_message")
  @Schema(description = "Consent message to show at login")
  private String consentMessage;

  @JsonProperty("platform_consent_confirm_text")
  @Schema(description = "Consent confirmation message")
  private String consentConfirmText;
}
