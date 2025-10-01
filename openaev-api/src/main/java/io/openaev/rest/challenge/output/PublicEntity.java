package io.openaev.rest.challenge.output;

// Implement for PublicExercise(exercise package) and ScenarioExercise(scenario challenge)

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PublicEntity {

  @JsonProperty("id")
  private String id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;
}
