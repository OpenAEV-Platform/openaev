package io.openaev.api.xtm_composer.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
@JsonInclude(NON_NULL)
public class XtmComposerOutput {
  @Schema(description = "XTM Composer Id")
  @JsonProperty("xtm_composer_id")
  @NotBlank
  private String id;

  @Schema(description = "XTM Composer Version")
  @JsonProperty("xtm_composer_version")
  @NotBlank
  private String version;
}
