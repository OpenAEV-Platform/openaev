package io.openaev.rest.exercise.service;

import io.openaev.database.repository.PauseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
public class PauseExerciseService {
  private final PauseRepository pauseRepository;

  public void deleteAllPauseByExerciseId(String exerciseId) {
    pauseRepository.deleteAllPauseByExerciseId(exerciseId);
  }
}
