package io.openaev.rest.kill_chain_phase.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class KillChainPhaseCreateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phase_kill_chain_name")
  private String killChainName;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phase_name")
  private String name;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phase_shortname")
  private String shortName;

  @JsonProperty("phase_stix_id")
  private String stixId;

  // The entity is @NotBlank and the frontend form already treats it as required: validating here
  // fails fast with a 400 instead of a late entity-validation failure at flush time.
  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phase_external_id")
  private String externalId;

  @JsonProperty("phase_description")
  private String description;

  @JsonProperty("phase_order")
  private Long order = 0L;
}
