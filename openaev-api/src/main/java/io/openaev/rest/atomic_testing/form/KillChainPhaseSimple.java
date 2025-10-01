package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class KillChainPhaseSimple {

  @JsonProperty("phase_id")
  @NotBlank
  private String id;

  @JsonProperty("phase_name")
  private String name;
}
