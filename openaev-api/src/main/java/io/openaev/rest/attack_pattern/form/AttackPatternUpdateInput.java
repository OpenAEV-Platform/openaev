package io.openaev.rest.attack_pattern.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttackPatternUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("attack_pattern_name")
  private String name;

  @JsonProperty("attack_pattern_description")
  private String description;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("attack_pattern_external_id")
  private String externalId;

  @JsonProperty("attack_pattern_kill_chain_phases")
  private List<String> killChainPhasesIds = new ArrayList<>();
}
