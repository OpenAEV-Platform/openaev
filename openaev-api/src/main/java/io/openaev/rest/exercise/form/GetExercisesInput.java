package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GetExercisesInput {
  @JsonProperty("exercise_ids")
  private List<String> exerciseIds = new ArrayList<>();
}
