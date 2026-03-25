package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChainingOutput {

  @JsonProperty("conditions")
  private List<EventOutput> conditions;

  @JsonProperty("steps")
  private List<StepOutput> steps;
}
