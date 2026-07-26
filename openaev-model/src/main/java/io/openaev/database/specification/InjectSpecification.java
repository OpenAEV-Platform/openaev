package io.openaev.database.specification;

import static io.openaev.database.model.ExerciseStatus.RUNNING;

import io.openaev.database.model.CollectExecutionStatus;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

  // -- FROM PARENT --

  public static Specification<Inject> fromSimulation(String simulationId) {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("id"), simulationId);
  }

  public static Specification<Inject> fromRunningSimulation() {
    return (root, query, cb) -> cb.equal(root.get("exercise").get("status"), RUNNING);
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
      return Specification.unrestricted();
    }
    return fromSimulation(scenarioOrSimulationId).or(fromScenario(scenarioOrSimulationId));
  }

  // -- STATUS --

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
          cb.equal(exercisePath.get("status"), RUNNING), // fromRunningExercise
          cb.isNull(root.join("status", JoinType.LEFT).get("name")) // notExecuted
          );
    };
  }

  /**
   * Coarse SQL predicate keeping only injects whose planned date can already be reached: the
   * exercise started at least {@code dependsDuration} seconds ago, or a trigger-now was requested.
   * Pauses only push the planned date later, so this is a safe superset of the exact in-memory
   * check ({@code isBeforeOrEqualsNow}) which must still be applied afterwards.
   *
   * <p>The reference is rounded up by one second on purpose: {@link Instant#getEpochSecond()}
   * truncates to whole seconds while {@code date_part('epoch', start)} is fractional. Without the
   * rounding {@code elapsedSeconds} could be under-counted by up to ~1s and wrongly exclude an
   * inject whose {@code dependsDuration} was just reached (it would never be loaded, so the
   * in-memory check could not re-include it). Over-counting by up to 1s only widens the candidate
   * set, which the exact check then prunes, so this stays a true superset.
   *
   * @param now the reference instant
   * @return the constructed specification
   */
  public static Specification<Inject> plannedDateReachable(Instant now) {
    return (root, query, cb) -> {
      Path<Object> exercisePath = root.get("exercise");
      Expression<Double> startEpochSeconds =
          cb.function(
              "date_part", Double.class, cb.literal("epoch"), exercisePath.<Instant>get("start"));
      Expression<Double> elapsedSeconds =
          cb.diff(cb.literal((double) (now.getEpochSecond() + 1)), startEpochSeconds);
      return cb.or(
          cb.isNotNull(root.get("triggerNowDate")),
          cb.lessThanOrEqualTo(root.get("dependsDuration").as(Double.class), elapsedSeconds));
    };
  }

  public static Specification<Inject> forAtomicTesting() {
    return Specification.<Inject>unrestricted()
        .and(isAtomicTesting())
        .and((root, query, cb) -> cb.equal(root.get("status").get("name"), ExecutionStatus.QUEUING))
        .and(
            (root, query, cb) ->
                cb.notEqual(root.get("status").get("name"), ExecutionStatus.PENDING));
  }

  public static Specification<Inject> pendingInjectWithThresholdMinutes(int thresholdMinutes) {
    return (root, query, cb) -> {
      Instant thresholdInstant = Instant.now().minus(Duration.ofMinutes(thresholdMinutes));

      // Subquery: simulation IDs that have an active chaining workflow (status = RUN).
      // The time-based engine must never touch injects owned by the chaining engine.
      Subquery<String> chainingSimIds = query.subquery(String.class);
      Root<Workflow> wf = chainingSimIds.from(Workflow.class);
      chainingSimIds
          .select(wf.get("simulation").get("id"))
          .where(cb.equal(wf.get("status"), WorkflowStatus.RUN));

      return cb.and(
          cb.equal(root.get("status").get("name"), ExecutionStatus.PENDING),
          cb.lessThan(root.get("status").get("trackingSentDate"), thresholdInstant),
          cb.or(
              cb.isNull(root.get("exercise")),
              cb.not(root.get("exercise").get("id").in(chainingSimIds))));
    };
  }

  public static Specification<Inject> hasStatus(List<ExecutionStatus> statuses) {
    return (root, query, cb) -> root.get("status").get("name").in(statuses);
  }

  public static Specification<Inject> hasCollectingStatus(List<CollectExecutionStatus> statuses) {
    return (root, query, cb) -> root.get("collectExecutionStatus").in(statuses);
  }

  // -- CONTRACT --

  public static Specification<Inject> fromContract(@NotBlank final String contract) {
    return (root, query, cb) ->
        cb.equal(root.get("injectorContract").get("compositeId").get("id"), contract);
  }

  // -- TEST --

  public static final Set<String> VALID_TESTABLE_TYPES =
      new HashSet<>(Arrays.asList("openaev_email", "openaev_ovh_sms"));

  public static Specification<Inject> testable() {
    return (root, query, cb) -> {
      if (query != null) {
        query.distinct(true);
      }
      return root.join("injectorContract")
          .join("injectorLinks")
          .join("injector")
          .get("type")
          .in(VALID_TESTABLE_TYPES);
    };
  }

  public static Specification<Inject> isAtomicTesting() {
    return (root, query, cb) ->
        cb.and(cb.isNull(root.get("scenario")), cb.isNull(root.get("exercise")));
  }

  // -- RECURRENCE (atomic testing scheduling) --

  public static Specification<Inject> isRecurring() {
    return (root, query, cb) -> cb.isNotNull(root.get("recurrence"));
  }

  public static Specification<Inject> recurrenceStartDateBefore(@NotNull final Instant startDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceStart")),
            cb.lessThanOrEqualTo(root.get("recurrenceStart"), startDate));
  }

  public static Specification<Inject> recurrenceStopDateAfter(@NotNull final Instant stopDate) {
    return (root, query, cb) ->
        cb.or(
            cb.isNull(root.get("recurrenceEnd")),
            cb.greaterThanOrEqualTo(root.get("recurrenceEnd"), stopDate));
  }

  /** Strictly matches recurrences with an end date at or before the given instant. */
  public static Specification<Inject> recurrenceStopDateBefore(@NotNull final Instant stopDate) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("recurrenceEnd"), stopDate);
  }
}
