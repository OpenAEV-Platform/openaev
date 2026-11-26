package io.openaev.api.xtm_composer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** What XTM Composer reports about itself on each connectivity refresh. Every field is optional. */
@Getter
@Setter
public class XtmComposerConnectivityInput {
  @Schema(description = "The XTM Composer version")
  @JsonProperty("version")
  private String version;

  @Schema(description = "The container image XTM Composer runs from")
  @JsonProperty("image")
  private String image;
}
