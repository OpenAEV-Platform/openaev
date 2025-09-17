package io.openbas.api.xtmhub;

import static io.openbas.config.AppConfig.MANDATORY_MESSAGE;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XtmHubRegisterInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @Schema(description = "The registration token")
  private String token;
}
