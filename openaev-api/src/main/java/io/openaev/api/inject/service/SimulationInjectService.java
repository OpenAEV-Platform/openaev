package io.openaev.api.inject.service;

import io.openaev.api.exercise.service.ExerciseService;
import io.openaev.database.model.Exercise;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class SimulationInjectService {

  private final ExerciseService exerciseService;
  private final InjectService injectService;

  public void deleteInject(@NotBlank final String exerciseId, @NotBlank final String injectId) {
    Exercise exercise = this.exerciseService.exercise(exerciseId);
    injectService.delete(injectId);
    this.exerciseService.updateExercise(exercise);
  }
}
