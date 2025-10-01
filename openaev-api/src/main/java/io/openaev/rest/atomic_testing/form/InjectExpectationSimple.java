package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class InjectExpectationSimple {

  @JsonProperty("inject_expectation_id")
  @NotBlank
  private String id;

  @JsonProperty("inject_expectation_name")
  private String name;
}
