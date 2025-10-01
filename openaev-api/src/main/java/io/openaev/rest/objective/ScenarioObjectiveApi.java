package io.openaev.rest.objective;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.helper.DatabaseHelper.resolveRelation;
import static java.time.Instant.now;

import io.openaev.aop.RBAC;
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
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioObjectiveApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/scenarios/";

  private final ScenarioRepository scenarioRepository;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final UserRepository userRepository;

  // region objectives
  @GetMapping(SCENARIO_URI + "{scenarioId}/objectives")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Objective> getMainObjectives(@PathVariable String scenarioId) {
    return objectiveRepository.findAll(ObjectiveSpecification.fromScenario(scenarioId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/objectives")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Objective createObjective(
      @PathVariable String scenarioId, @Valid @RequestBody ObjectiveInput input) {
    Scenario scenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);
    Objective objective = new Objective();
    objective.setUpdateAttributes(input);
    objective.setScenario(scenario);
    return objectiveRepository.save(objective);
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Objective updateObjective(
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @Valid @RequestBody ObjectiveInput input) {
    Objective objective =
        objectiveRepository.findById(objectiveId).orElseThrow(ElementNotFoundException::new);
    objective.setUpdateAttributes(input);
    return objectiveRepository.save(objective);
  }

  @DeleteMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteObjective(@PathVariable String scenarioId, @PathVariable String objectiveId) {
    objectiveRepository.deleteById(objectiveId);
  }

  // endregion

  // region evaluations
  @GetMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Evaluation getEvaluation(
      @PathVariable String scenarioId, @PathVariable String evaluationId) {
    return evaluationRepository.findById(evaluationId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<Evaluation> getEvaluations(
      @PathVariable String scenarioId, @PathVariable String objectiveId) {
    return evaluationRepository.findAll(EvaluationSpecification.fromObjective(objectiveId));
  }

  @PostMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Evaluation createEvaluation(
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @Valid @RequestBody EvaluationInput input) {
    Evaluation evaluation = new Evaluation();
    evaluation.setUpdateAttributes(input);
    Objective objective = resolveRelation(objectiveId, objectiveRepository);
    evaluation.setObjective(objective);
    evaluation.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    Evaluation result = evaluationRepository.save(evaluation);
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    Scenario scenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);
    scenario.setUpdatedAt(now());
    scenarioRepository.save(scenario);
    return result;
  }

  @PutMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Evaluation updateEvaluation(
      @PathVariable String scenarioId,
      @PathVariable String objectiveId,
      @PathVariable String evaluationId,
      @Valid @RequestBody EvaluationInput input) {
    Evaluation evaluation =
        evaluationRepository.findById(evaluationId).orElseThrow(ElementNotFoundException::new);
    evaluation.setUpdateAttributes(input);
    Evaluation result = evaluationRepository.save(evaluation);
    Objective objective =
        objectiveRepository.findById(objectiveId).orElseThrow(ElementNotFoundException::new);
    objective.setUpdatedAt(now());
    objectiveRepository.save(objective);
    Scenario scenario =
        scenarioRepository.findById(scenarioId).orElseThrow(ElementNotFoundException::new);
    scenario.setUpdatedAt(now());
    scenarioRepository.save(scenario);
    return result;
  }

  @DeleteMapping(SCENARIO_URI + "{scenarioId}/objectives/{objectiveId}/evaluations/{evaluationId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteEvaluation(@PathVariable String scenarioId, @PathVariable String evaluationId) {
    evaluationRepository.deleteById(evaluationId);
  }
  // endregion
}
