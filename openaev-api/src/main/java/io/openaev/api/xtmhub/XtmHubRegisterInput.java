package io.openaev.api.xtmhub;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

  @Size(max = 255)
  @JsonProperty("organization_name")
  @Schema(description = "The name of the XTM Hub organization the platform is registered in")
  private String organizationName;
}
