package io.openaev.injectors.phishing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.injectors.common.model.BaseInjectContent;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Inject content for the internal phishing injector. The landing page is resolved from the {@code
 * InjectorContract} id (each landing page synthesizes a contract whose id equals the landing page
 * id), so the content only carries the chosen email template plus optional subject/sender
 * overrides.
 */
@Getter
@Setter
public class PhishingContent extends BaseInjectContent {

  /** Id of the {@code PhishingEmailTemplate} used as the lure email. */
  @JsonProperty("emailTemplate")
  private String emailTemplate;

  /** Optional subject override (falls back to the email template subject). */
  @JsonProperty("subject")
  private String subject;

  /**
   * Optional sender display name override (falls back to the email template / platform default).
   */
  @JsonProperty("fromName")
  private String fromName;

  /** Optional sender address override (falls back to the email template / platform default). */
  @JsonProperty("fromEmail")
  private String fromEmail;

  public PhishingContent() {
    // For mapper
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PhishingContent that = (PhishingContent) o;
    return Objects.equals(emailTemplate, that.emailTemplate)
        && Objects.equals(subject, that.subject)
        && Objects.equals(fromName, that.fromName)
        && Objects.equals(fromEmail, that.fromEmail);
  }

  @Override
  public int hashCode() {
    return Objects.hash(emailTemplate, subject, fromName, fromEmail);
  }
}
