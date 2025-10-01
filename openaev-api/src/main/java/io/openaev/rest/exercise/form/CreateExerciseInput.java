package io.openaev.rest.exercise.form;

import static io.openaev.config.AppConfig.NOW_FUTURE_MESSAGE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateExerciseInput extends ExerciseInput {

  @Schema(nullable = true)
  @JsonProperty("exercise_start_date")
  @FutureOrPresent(message = NOW_FUTURE_MESSAGE)
  private Instant start;

  @JsonProperty("exercise_custom_dashboard")
  private String customDashboard;

  public Instant getStart() {
    return start != null ? start.truncatedTo(ChronoUnit.MINUTES) : null;
  }
}
