package io.openaev.rest.exercise;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.response.PublicExercise;
import io.openaev.rest.helper.RestBehavior;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExercisePlayerApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/player/exercises";

  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  @GetMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public PublicExercise playerExercise(
      @PathVariable String exerciseId, @RequestParam Optional<String> userId) {
    impersonateUser(this.userRepository, userId);
    Exercise exercise =
        this.exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    return new PublicExercise(exercise);
  }
}
