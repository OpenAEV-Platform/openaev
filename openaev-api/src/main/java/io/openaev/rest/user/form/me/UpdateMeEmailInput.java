package io.openaev.rest.user.form.me;

import static io.openaev.api.users.dto.UserOutput.ALIAS_EMAIL;
import static io.openaev.config.AppConfig.EMAIL_FORMAT;
import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMeEmailInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("user_current_password")
  private String currentPassword;

  @NotBlank(message = MANDATORY_MESSAGE)
  @Email(message = EMAIL_FORMAT)
  @JsonProperty(ALIAS_EMAIL)
  private String email;
}
