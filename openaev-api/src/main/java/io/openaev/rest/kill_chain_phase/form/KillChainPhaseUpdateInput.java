package io.openaev.rest.kill_chain_phase.form;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class KillChainPhaseUpdateInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phase_kill_chain_name")
  private String killChainName;

  @NotBlank(message = MANDATORY_MESSAGE)
  @JsonProperty("phase_name")
  private String name;

  @JsonProperty("phase_order")
  private Long order;
}
