package io.openbas.database.specification;

import io.openbas.database.model.ExecutionStatus;
import io.openbas.database.model.ExerciseStatus;
import io.openbas.database.model.Inject;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

public class InjectSpecification {

  private InjectSpecification() {}

  /**
   * Create a specification to get an exercise
   *
   * @deprecated Use fromSimulation instead
   * @param exerciseId the exercice ID to search
   * @return the built specification
   */
  @Deprecated(since = "1.11.0", forRemoval = true)
  public static Specification<Inject> fromExercise(String exerciseId) {
    return fromSimulation(exerciseId);
  }

  public static Specification<Inject> fromSimulation(String simulationId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), simulationId);
  }

  public static Specification<Inject> fromScenario(String scenarioId) {
    return (root, query, cb) -> cb.equal(root.get("scenario").get("id"), scenarioId);
  }

  /**
   * Get injects from a scenario or a simulation
   *
   * @param scenarioOrSimulationId the id of the scenario or the simulation
   * @return the constructed specification
   */
  public static Specification<Inject> fromScenarioOrSimulation(String scenarioOrSimulationId) {
    if (StringUtils.isBlank(scenarioOrSimulationId)) {
      // Return an empty specification
      return Specification.where(null);
    }
    return fromSimulation(scenarioOrSimulationId).or(fromScenario(scenarioOrSimulationId));
  }

  public static Specification<Inject> next() {
    return (root, query, cb) -> {
      Path<Object> exercisePath = root.get("exercise");
      return cb.and(
          cb.equal(root.get("enabled"), true), // isEnable
          cb.isNotNull(exercisePath.get("start")), // fromScheduled
          cb.isNull(root.join("status", JoinType.LEFT).get("name")) // notExecuted
          );
    };
  }

  public static Specification<Inject> executable() {
    return (root, query, cb) -> {
      Path<Object> exercisePath = root.get("exercise");
      return cb.and(
          // cb.notEqual(root.get("type"), ManualContract.TYPE),  // notManual
          cb.equal(root.get("enabled"), true), // isEnable
          cb.isNotNull(exercisePath.get("start")), // fromScheduled
          cb.equal(exercisePath.get("status"), ExerciseStatus.RUNNING), // fromRunningExercise
          cb.isNull(root.join("status", JoinType.LEFT).get("name")) // notExecuted
          );
    };
  }

  public static Specification<Inject> forAtomicTesting() {
    return Specification.where(isAtomicTesting())
        .and((root, query, cb) -> cb.equal(root.get("status").get("name"), ExecutionStatus.QUEUING))
        .and(
            (root, query, cb) ->
                cb.notEqual(root.get("status").get("name"), ExecutionStatus.PENDING));
  }

  public static Specification<Inject> pendingInjectWithThresholdMinutes(int thresholdMinutes) {
    return (root, query, cb) -> {
      Instant thresholdInstant = Instant.now().minus(Duration.ofMinutes(thresholdMinutes));
      return cb.and(
          cb.equal(root.get("status").get("name"), ExecutionStatus.PENDING),
          cb.lessThan(root.get("status").get("trackingSentDate"), thresholdInstant));
    };
  }

  public static Specification<Inject> fromContract(@NotBlank final String contract) {
    return (root, query, cb) -> cb.equal(root.get("injectorContract").get("id"), contract);
  }

  public static final Set<String> VALID_TESTABLE_TYPES =
      new HashSet<>(Arrays.asList("openbas_email", "openbas_ovh_sms"));

  public static Specification<Inject> byIds(List<String> injectIds) {
    return (root, query, cb) -> root.get("id").in(injectIds);
  }

  public static Specification<Inject> testable() {
    return (root, query, cb) ->
        root.get("injectorContract").get("injector").get("type").in(VALID_TESTABLE_TYPES);
  }

  public static Specification<Inject> isAtomicTesting() {
    return (root, query, cb) ->
        cb.and(cb.isNull(root.get("scenario")), cb.isNull(root.get("exercise")));
  }
}
