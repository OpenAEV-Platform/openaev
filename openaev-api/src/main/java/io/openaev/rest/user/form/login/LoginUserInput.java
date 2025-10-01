package io.openaev.rest.user.form.login;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @Schema(description = "The identifier of the user")
  private String login;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty
  @Schema(description = "The password of the user")
  private String password;
}
