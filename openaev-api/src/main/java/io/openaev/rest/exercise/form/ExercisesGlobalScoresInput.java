package io.openaev.rest.exercise.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ExercisesGlobalScoresInput(
    @JsonProperty("exercise_ids") @NotNull List<String> exerciseIds) {}
