package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ExerciseUpdateTeamsInput {

  @JsonProperty("exercise_teams")
  private List<String> teamIds = new ArrayList<>();
}
