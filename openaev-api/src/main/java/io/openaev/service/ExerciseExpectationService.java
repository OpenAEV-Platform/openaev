package io.openaev.service;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ExerciseExpectationService {

  private final InjectExpectationRepository injectExpectationRepository;
  private final ExerciseRepository exerciseRepository;

  public List<InjectExpectation> injectExpectations(@NotBlank final String exerciseId) {
    Exercise exercise =
        this.exerciseRepository
            .findById(exerciseId)
            .orElseThrow(
                () -> new ElementNotFoundException("Exercise not found with id: " + exerciseId));
    return this.injectExpectationRepository.findAllForExercise(exercise.getId());
  }
}
