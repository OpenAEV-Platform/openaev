package io.openaev.rest.user.form.me;

import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.AuditLogIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileInput {

  @Email(message = EMAIL_FORMAT)
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_email")
  @AuditLogIgnore
  private String email;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_firstname")
  @AuditLogIgnore
  private String firstname;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_lastname")
  @AuditLogIgnore
  private String lastname;

  @JsonProperty("user_organization")
  private String organizationId;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_lang")
  @AuditLogIgnore
  private String lang;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_theme")
  private String theme;

  @JsonProperty("user_country")
  @AuditLogIgnore
  private String country;

  @JsonProperty("user_home_dashboard")
  private String homeDashboard;
}
