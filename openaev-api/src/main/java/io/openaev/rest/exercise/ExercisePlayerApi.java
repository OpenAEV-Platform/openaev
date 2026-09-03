package io.openaev.rest.exercise;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.UrlAccessControl;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.response.PublicExercise;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.security.error.AuthenticationError;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ExercisePlayerApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/player/exercises";
  private static final String TENANT_EXERCISE_URI = TENANT_PREFIX + "/player/exercises";

  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  @GetMapping({EXERCISE_URI + "/{exerciseId}", TENANT_EXERCISE_URI + "/{exerciseId}"})
  @Transactional
  @AccessControl(skipRBAC = true)
  @UrlAccessControl(exerciseId = "#exerciseId", userId = "#userId")
  public PublicExercise playerExercise(
      @PathVariable String exerciseId, @RequestParam Optional<String> userId, TxCtx ctx)
      throws AuthenticationError {
    impersonateUser(this.userRepository, userId);
    Exercise exercise =
        this.exerciseRepository
            .findByIdAndTenantId(exerciseId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    return new PublicExercise(exercise);
  }
}
