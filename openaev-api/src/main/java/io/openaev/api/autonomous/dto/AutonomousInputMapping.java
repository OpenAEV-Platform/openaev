package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * Binds a finding value produced upstream into one of this step's inject inputs, so the step runs
 * against what earlier steps discovered instead of a hard-coded value. Mirrors a {@code MAPPER}
 * condition. Example: {@code {input_key: "host", key_type: "host"}} writes the discovered host into
 * the inject content's {@code host} field; {@code {input_key: "port", key_type: "port"}} writes the
 * discovered port. This is what lets one seed scan fan out onto every host/port it found.
 */
@Getter
@Setter
@Schema(description = "Binds an upstream finding value into one of this step's inject inputs")
public class AutonomousInputMapping {

  @JsonProperty("input_key")
  @Schema(
      description =
          "The inject content field to fill (e.g. host, port, username, password) - the key the"
              + " injector contract reads its input from.")
  private String inputKey;

  @JsonProperty("key_type")
  @Schema(
      description =
          "The finding primitive to pull the value from, as its lowercase label (e.g. host, port,"
              + " username, password, hash). Must match a primitive an upstream step emits.")
  private PrimitiveType keyType;

  @JsonProperty("mapping_type")
  @Schema(
      description =
          "Where to read the value from: GLOBAL (workflow-wide finding pool, the default and usual"
              + " choice), LOCAL (this step's own matched values), or DEFAULT (static).")
  private MappingType mappingType;
}
