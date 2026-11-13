package io.openaev.rest.objective;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static java.time.Instant.now;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.EvaluationRepository;
import io.openaev.database.repository.ObjectiveRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.EvaluationSpecification;
import io.openaev.database.specification.ObjectiveSpecification;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.objective.form.EvaluationInput;
import io.openaev.rest.objective.form.ObjectiveInput;
import io.openaev.service.scenario.ScenarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioObjectiveApi extends RestBehavior {

  private final ScenarioRepository scenarioRepository;
  private final ScenarioService scenarioService;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final UserRepository userRepository;

  // region objectives
  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/objectives",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Objective> getMainObjectives(TxCtx ctx, @PathVariable String scenarioId) {
    return objectiveRepository.findAll(ObjectiveSpecification.fromScenario(scenarioId));
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/objectives",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public Objective createObjective(
      TxCtx ctx, @PathVariable String scenarioId, @Valid @RequestBody ObjectiveInput input) {
    Scenario scenario =
        scenarioService.findInScope(ctx, scenarioId).orElseThrow(ElementNotFoundException::new);
    Objective objective = new Objective();
    objective.setUpdateAttributes(input);
    objective.setScenario(scenario);
    return objectiveRepository.save(objective);
  }

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Objective updateObjective(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @Valid @RequestBody ObjectiveInput input) {
    Objective objective = objectiveOfScenario(scenarioId, objectiveId);
    objective.setUpdateAttributes(input);
    return objectiveRepository.save(objective);
  }

  @DeleteMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteObjective(
      TxCtx ctx, @PathVariable String scenarioId, @PathVariable String objectiveId) {
    objectiveRepository.delete(objectiveOfScenario(scenarioId, objectiveId));
  }

  // endregion

  // region evaluations
  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Evaluation getEvaluation(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId) {
    return evaluationOfObjective(scenarioId, objectiveId, evaluationId);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Evaluation> getEvaluations(
      TxCtx ctx, @PathVariable String scenarioId, @PathVariable String objectiveId) {
    objectiveOfScenario(scenarioId, objectiveId);
    return evaluationRepository.findAll(EvaluationSpecification.fromObjective(objectiveId));
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public Evaluation createEvaluation(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @Valid @RequestBody EvaluationInput input) {
    Evaluation evaluation = new Evaluation();
    evaluation.setUpdateAttributes(input);
    Objective objective = objectiveOfScenario(scenarioId, objectiveId);
    evaluation.setObjective(objective);
    evaluation.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    Evaluation result = evaluationRepository.save(evaluation);
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    Scenario scenario =
        scenarioService.findInScope(ctx, scenarioId).orElseThrow(ElementNotFoundException::new);
    scenario.setUpdatedAt(now());
    scenarioRepository.save(scenario);
    return result;
  }

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Evaluation updateEvaluation(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId,
      @Valid @RequestBody EvaluationInput input) {
    Evaluation evaluation = evaluationOfObjective(scenarioId, objectiveId, evaluationId);
    evaluation.setUpdateAttributes(input);
    Evaluation result = evaluationRepository.save(evaluation);
    Objective objective = evaluation.getObjective();
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    Scenario scenario =
        scenarioService.findInScope(ctx, scenarioId).orElseThrow(ElementNotFoundException::new);
    scenario.setUpdatedAt(now());
    scenarioRepository.save(scenario);
    return result;
  }

  @DeleteMapping({
    SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}",
    TENANT_SCENARIO_URI + "/{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteEvaluation(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId) {
    evaluationRepository.delete(evaluationOfObjective(scenarioId, objectiveId, evaluationId));
  }

  // endregion

  /** The objective, only when it belongs to the given scenario. */
  private Objective objectiveOfScenario(String scenarioId, String objectiveId) {
    return objectiveRepository
        .findById(objectiveId)
        .filter(
            objective ->
                objective.getScenario() != null
                    && scenarioId.equals(objective.getScenario().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }

  /** The evaluation, only when it belongs to the objective of the path, itself in the scenario. */
  private Evaluation evaluationOfObjective(
      String scenarioId, String objectiveId, String evaluationId) {
    return evaluationRepository
        .findById(evaluationId)
        .filter(
            evaluation ->
                evaluation.getObjective() != null
                    && objectiveId.equals(evaluation.getObjective().getId())
                    && evaluation.getObjective().getScenario() != null
                    && scenarioId.equals(evaluation.getObjective().getScenario().getId()))
        .orElseThrow(ElementNotFoundException::new);
  }
}
