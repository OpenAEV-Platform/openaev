package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class UpdateExerciseInput extends ExerciseInput {
  @JsonProperty("apply_tag_rule")
  private boolean applyTagRule = false;

  @JsonProperty("exercise_custom_dashboard")
  private String customDashboard;
}
