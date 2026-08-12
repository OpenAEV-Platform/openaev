package io.openaev.database.specification;

import static io.openaev.database.model.ExerciseStatus.RUNNING;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.CollectExecutionStatus;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.TableTopInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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
import java.util.function.Function;
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

  public static Specification<Inject> stalledInjectWithThresholdMinutes(int thresholdMinutes) {
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
          root.get("status").get("name").in(ExecutionStatus.STALLED_STATUSES),
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

  // -- TARGET SCOPES ("Injects played" sections of the detail pages) --
  //
  // Each targetsXxx specification matches injects that concern a given entity through BOTH the
  // configured targeting (join tables, resolvable before any execution) AND the execution
  // evidence (the expectations persisted when the inject was played - which also covers targeting
  // modes that cannot be resolved with a SQL join, e.g. dynamic asset-group membership). This is
  // the same population the posture/expectation KPIs are computed from on those pages, so the
  // inject lists and the expectation counters stay consistent.

  /** EXISTS (SELECT 1 FROM injects i WHERE i.id = root.id AND targetIdPath(i) = targetId). */
  private static Predicate existsInjectTargeting(
      Root<Inject> root,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Function<Root<Inject>, Expression<?>> targetIdPath,
      String targetId) {
    Subquery<Integer> sub = query.subquery(Integer.class);
    Root<Inject> inject = sub.from(Inject.class);
    sub.select(cb.literal(1))
        .where(
            cb.equal(inject.get("id"), root.get("id")),
            cb.equal(targetIdPath.apply(inject), targetId));
    return cb.exists(sub);
  }

  /** EXISTS an expectation of the given type linked to the root inject and the target entity. */
  private static <E extends BaseInjectExpectation> Predicate existsExpectationTargeting(
      Class<E> expectationType,
      Root<Inject> root,
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      Function<Root<E>, Expression<?>> targetIdPath,
      String targetId) {
    Subquery<Integer> sub = query.subquery(Integer.class);
    Root<E> expectation = sub.from(expectationType);
    sub.select(cb.literal(1))
        .where(
            cb.equal(expectation.get("inject").get("id"), root.get("id")),
            cb.equal(targetIdPath.apply(expectation), targetId));
    return cb.exists(sub);
  }

  /**
   * Injects that concern a given asset: direct target ({@code injects_assets}), static member of a
   * targeted asset group, or evidenced by a technical expectation persisted for this asset at
   * execution time (covers dynamic asset-group membership).
   */
  public static Specification<Inject> targetsAsset(@NotBlank final String assetId) {
    return (root, query, cb) ->
        cb.or(
            existsInjectTargeting(root, query, cb, i -> i.join("assets").get("id"), assetId),
            existsInjectTargeting(
                root, query, cb, i -> i.join("assetGroups").join("assets").get("id"), assetId),
            existsExpectationTargeting(
                TechnicalInjectExpectation.class,
                root,
                query,
                cb,
                e -> e.get("asset").get("id"),
                assetId));
  }

  /**
   * Injects that concern a given asset group: direct target ({@code injects_asset_groups}) or
   * evidenced by a technical expectation persisted for this group at execution time.
   */
  public static Specification<Inject> targetsAssetGroup(@NotBlank final String assetGroupId) {
    return (root, query, cb) ->
        cb.or(
            existsInjectTargeting(
                root, query, cb, i -> i.join("assetGroups").get("id"), assetGroupId),
            existsExpectationTargeting(
                TechnicalInjectExpectation.class,
                root,
                query,
                cb,
                e -> e.get("assetGroup").get("id"),
                assetGroupId));
  }

  /**
   * Injects that concern a given team: direct target ({@code injects_teams}) or evidenced by a
   * table-top expectation (manual / article / challenge) persisted for this team.
   */
  public static Specification<Inject> targetsTeam(@NotBlank final String teamId) {
    return (root, query, cb) ->
        cb.or(
            existsInjectTargeting(root, query, cb, i -> i.join("teams").get("id"), teamId),
            existsExpectationTargeting(
                TableTopInjectExpectation.class,
                root,
                query,
                cb,
                e -> e.get("team").get("id"),
                teamId));
  }

  /**
   * Injects that concern a given player: targeted through one of the player's teams ({@code
   * injects_teams} x {@code users_teams}) or evidenced by a player-level table-top expectation.
   */
  public static Specification<Inject> targetsPlayer(@NotBlank final String userId) {
    return (root, query, cb) ->
        cb.or(
            existsInjectTargeting(
                root, query, cb, i -> i.join("teams").join("users").get("id"), userId),
            existsExpectationTargeting(
                TableTopInjectExpectation.class,
                root,
                query,
                cb,
                e -> e.get("user").get("id"),
                userId));
  }

  /**
   * Injects that concern a given organization: targeted through one of the organization's teams or
   * evidenced by a table-top expectation persisted for such a team.
   */
  public static Specification<Inject> targetsOrganization(@NotBlank final String organizationId) {
    return (root, query, cb) ->
        cb.or(
            existsInjectTargeting(
                root,
                query,
                cb,
                i -> i.join("teams").get("organization").get("id"),
                organizationId),
            existsExpectationTargeting(
                TableTopInjectExpectation.class,
                root,
                query,
                cb,
                e -> e.get("team").get("organization").get("id"),
                organizationId));
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

  /** Matches recurrences bounded by an end date (only those can ever become outdated). */
  public static Specification<Inject> hasRecurrenceEnd() {
    return (root, query, cb) -> cb.isNotNull(root.get("recurrenceEnd"));
  }
}
