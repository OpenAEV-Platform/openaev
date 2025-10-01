package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.TargetType;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class TargetSimple {

  @JsonProperty("target_id")
  @NotBlank
  private String id;

  @JsonProperty("target_name")
  private String name;

  @JsonProperty("target_type")
  private TargetType type;
}
