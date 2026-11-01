package io.openaev.service;

import io.openaev.context.TxCtx;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExerciseExpectationService {

  private final InjectExpectationRepository injectExpectationRepository;
  private final ExerciseService exerciseService;

  /** Lists the expectations of a simulation resolved inside the request's tenant scope. */
  public List<BaseInjectExpectation> injectExpectations(
      final TxCtx ctx, @NotBlank final String exerciseId) {
    Exercise exercise =
        this.exerciseService
            .findByIdInScope(ctx, exerciseId)
            .orElseThrow(
                () -> new ElementNotFoundException("Exercise not found with id: " + exerciseId));
    return this.injectExpectationRepository.findAllForExercise(exercise.getId());
  }
}
