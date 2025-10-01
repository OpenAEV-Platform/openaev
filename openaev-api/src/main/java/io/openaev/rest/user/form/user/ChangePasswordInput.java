package io.openaev.rest.user.form.user;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("password")
  @Schema(description = "The new password")
  private String password;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("password_validation")
  @Schema(description = "The new password again to validate it's been typed well")
  private String passwordValidation;
}
