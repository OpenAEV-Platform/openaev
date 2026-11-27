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

  // Kept as the OpenAEV version XTM Composer registered against: released XTM Composers compare
  // it with GET /api/settings/version and register again when they differ.
  @Schema(description = "The OpenAEV version XTM Composer registered against")
  @JsonProperty("xtm_composer_version")
  @NotBlank
  private String version;

  @Schema(description = "The version XTM Composer reported, 'unknown' when it reported none")
  @JsonProperty("xtm_composer_reported_version")
  private String reportedVersion;

  @Schema(description = "The container image XTM Composer reported, empty when it reported none")
  @JsonProperty("xtm_composer_image")
  private String image;
}
