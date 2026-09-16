package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/** A single value format rule of a primitive chaining type. */
@Getter
@Builder
@Schema(description = "A single value format rule.")
public class PrimitiveTypeFormatRuleOutput {

  @Schema(
      description =
          "Identifier of the rule. When no pattern is exposed, the frontend must provide its own"
              + " implementation for this identifier, pinned by the shared test vectors.")
  @JsonProperty("kind")
  private String kind;

  @Schema(
      description =
          "Pattern to apply, written in the Java / ECMAScript intersection so it can be passed"
              + " straight to RegExp. Absent for rules backed by a parser that no portable regex"
              + " can express (IP addresses, subnets, email).")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonProperty("pattern")
  private String pattern;

  @Schema(
      description =
          "Stable key for the error message, to be translated by the frontend. Never a"
              + " pre-translated sentence.")
  @JsonProperty("error_message_key")
  private String errorMessageKey;
}
