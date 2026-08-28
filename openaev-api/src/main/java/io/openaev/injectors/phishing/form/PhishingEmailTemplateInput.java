package io.openaev.injectors.phishing.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Create/update payload for a reusable phishing email template. */
@Getter
@Setter
public class PhishingEmailTemplateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phishing_email_template_name")
  private String name;

  @JsonProperty("phishing_email_template_description")
  private String description;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phishing_email_template_subject")
  private String subject;

  @JsonProperty("phishing_email_template_html_body")
  private String htmlBody;

  @JsonProperty("phishing_email_template_text_body")
  private String textBody;

  @JsonProperty("phishing_email_template_from_name")
  private String fromName;

  @JsonProperty("phishing_email_template_from_email")
  private String fromEmail;

  @JsonProperty("phishing_email_template_add_tracking_pixel")
  private boolean addTrackingPixel = true;
}
