package io.openaev.rest.exercise.service;

import static java.time.Duration.between;
import static java.time.Instant.now;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Pause;
import io.openaev.database.repository.PauseRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
public class PauseExerciseService {
  private final PauseRepository pauseRepository;

  public void deleteAll(List<Pause> pausesOfExercise) {
    pauseRepository.deleteAll(pausesOfExercise);
  }

  public List<Pause> findAllForExercise(String exerciseId) {
    return pauseRepository.findAllForExercise(exerciseId);
  }

  public void endPauseByExercise(Instant lastPause, Exercise exercise) {
    Pause pause = new Pause();
    pause.setDate(lastPause);
    pause.setExercise(exercise);
    pause.setDuration(between(lastPause, now()).getSeconds());
    pauseRepository.save(pause);
  }
}
