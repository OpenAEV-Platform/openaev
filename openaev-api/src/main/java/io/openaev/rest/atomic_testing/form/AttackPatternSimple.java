package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AttackPatternSimple {

  @JsonProperty("attack_pattern_id")
  @NotBlank
  private String id;

  @JsonProperty("attack_pattern_name")
  @NotBlank
  private String name;

  @JsonProperty("attack_pattern_external_id")
  @NotBlank
  private String externalId;
}
