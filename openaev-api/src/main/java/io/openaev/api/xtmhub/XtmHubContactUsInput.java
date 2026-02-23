package io.openaev.api.xtmhub;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XtmHubContactUsInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @Schema(description = "The message sent")
  private String message;
}
