package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ExerciseStatus;

public class ExerciseUpdateStatusInput {
  @JsonProperty("exercise_status")
  private ExerciseStatus status;

  public ExerciseStatus getStatus() {
    return status;
  }

  public void setStatus(ExerciseStatus status) {
    this.status = status;
  }
}
