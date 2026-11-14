package io.openaev.rest.objective;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static java.time.Instant.now;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.EvaluationSpecification;
import io.openaev.database.specification.ObjectiveSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.objective.form.EvaluationInput;
import io.openaev.rest.objective.form.ObjectiveInput;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ExerciseObjectiveApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/exercises/";
  private static final String TENANT_EXERCISE_URI = TENANT_PREFIX + "/exercises/";

  private final ExerciseRepository exerciseRepository;
  private final ExerciseService exerciseService;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final UserRepository userRepository;

  // region objectives
  @GetMapping({
    EXERCISE_URI + "{exerciseId}/objectives",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Objective> getMainObjectives(TxCtx ctx, @PathVariable String exerciseId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    return objectiveRepository.findAll(ObjectiveSpecification.fromExercise(exerciseId));
  }

  @PostMapping({
    EXERCISE_URI + "{exerciseId}/objectives",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Objective createObjective(
      TxCtx ctx, @PathVariable String exerciseId, @Valid @RequestBody ObjectiveInput input) {
    Exercise exercise =
        exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    Objective objective = new Objective();
    objective.setUpdateAttributes(input);
    objective.setExercise(exercise);
    return objectiveRepository.save(objective);
  }

  @PutMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Objective updateObjective(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String objectiveId,
      @Valid @RequestBody ObjectiveInput input) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    Objective objective = objectiveOfExercise(exerciseId, objectiveId);
    objective.setUpdateAttributes(input);
    return objectiveRepository.save(objective);
  }

  @DeleteMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteObjective(
      TxCtx ctx, @PathVariable String exerciseId, @PathVariable String objectiveId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    objectiveRepository.delete(objectiveOfExercise(exerciseId, objectiveId));
  }

  // endregion

  // region evaluations
  @GetMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations/{evaluationId}",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations/{evaluationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Evaluation getEvaluation(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    return evaluationOfObjective(exerciseId, objectiveId, evaluationId);
  }

  @GetMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Evaluation> getEvaluations(
      TxCtx ctx, @PathVariable String exerciseId, @PathVariable String objectiveId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    objectiveOfExercise(exerciseId, objectiveId);
    return evaluationRepository.findAll(EvaluationSpecification.fromObjective(objectiveId));
  }

  @PostMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Evaluation createEvaluation(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String objectiveId,
      @Valid @RequestBody EvaluationInput input) {
    Exercise exercise =
        exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    Evaluation evaluation = new Evaluation();
    evaluation.setUpdateAttributes(input);
    Objective objective = objectiveOfExercise(exerciseId, objectiveId);
    evaluation.setObjective(objective);
    evaluation.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    Evaluation result = evaluationRepository.save(evaluation);
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    exercise.setUpdatedAt(now());
    exerciseRepository.save(exercise);
    return result;
  }

  @PutMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations/{evaluationId}",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations/{evaluationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Evaluation updateEvaluation(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId,
      @Valid @RequestBody EvaluationInput input) {
    Exercise exercise =
        exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    Evaluation evaluation = evaluationOfObjective(exerciseId, objectiveId, evaluationId);
    evaluation.setUpdateAttributes(input);
    Evaluation result = evaluationRepository.save(evaluation);
    Objective objective = evaluation.getObjective();
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    exercise.setUpdatedAt(now());
    exerciseRepository.save(exercise);
    return result;
  }

  @DeleteMapping({
    EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations/{evaluationId}",
    TENANT_EXERCISE_URI + "{exerciseId}/objectives/{objectiveId}/evaluations/{evaluationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteEvaluation(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId) {
    exerciseService.findByIdInScope(ctx, exerciseId).orElseThrow(ElementNotFoundException::new);
    evaluationRepository.delete(evaluationOfObjective(exerciseId, objectiveId, evaluationId));
  }

  // endregion

  /** The objective, only when it belongs to the given simulation. */
  private Objective objectiveOfExercise(String exerciseId, String objectiveId) {
    return objectiveRepository
        .findById(objectiveId)
        .filter(
            objective ->
                objective.getExercise() != null
                    && exerciseId.equals(objective.getExercise().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }

  /**
   * The evaluation, only when it belongs to the objective of the path, itself in the simulation.
   */
  private Evaluation evaluationOfObjective(
      String exerciseId, String objectiveId, String evaluationId) {
    return evaluationRepository
        .findById(evaluationId)
        .filter(
            evaluation ->
                evaluation.getObjective() != null
                    && objectiveId.equals(evaluation.getObjective().getId())
                    && evaluation.getObjective().getExercise() != null
                    && exerciseId.equals(evaluation.getObjective().getExercise().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }
}
