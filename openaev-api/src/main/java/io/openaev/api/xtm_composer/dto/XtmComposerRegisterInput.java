package io.openaev.api.xtm_composer.dto;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class XtmComposerRegisterInput {
  @NotBlank(message = MANDATORY_MESSAGE)
  @Schema(description = "The XTM Composer Id")
  private String id;

  @NotBlank(message = MANDATORY_MESSAGE)
  @Schema(description = "The XTM Composer Name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @Schema(description = "The registration public key")
  @JsonProperty("public_key")
  private String publicKey;
}
