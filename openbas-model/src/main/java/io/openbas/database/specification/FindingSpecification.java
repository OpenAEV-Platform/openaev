package io.openbas.database.specification;

import io.openbas.database.model.ExerciseStatus;
import io.openbas.database.model.Finding;
import jakarta.persistence.criteria.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class FindingSpecification {

  private FindingSpecification() {}

  public static Specification<Finding> findFindingsForInject(@NotNull final String injectId) {
    return (root, query, cb) -> cb.equal(root.get("inject").get("id"), injectId);
  }

  public static Specification<Finding> findFindingsForSimulation(
      @NotNull final String simulationId) {
    return (root, query, cb) ->
        cb.equal(root.get("inject").get("exercise").get("id"), simulationId);
  }

  public static Specification<Finding> findFindingsForScenario(@NotNull final String scenarioId) {
    return (root, query, cb) ->
        cb.equal(root.get("inject").get("exercise").get("scenario").get("id"), scenarioId);
  }

  public static Specification<Finding> findFindingsForEndpoint(@NotNull final String endpointId) {
    return (root, query, cb) -> cb.equal(root.get("assets").get("id"), endpointId);
  }

  public static Specification<Finding> forLatestSimulations() {
    return (root, query, cb) -> {
      Join<?, ?> exerciseJoin1 =
          root.join("inject", JoinType.INNER).join("exercise", JoinType.LEFT);
      Join<?, ?> exerciseJoin2 =
          exerciseJoin1.join("scenario", JoinType.LEFT).join("exercises", JoinType.LEFT);

      exerciseJoin2.on(
          cb.and(
              cb.equal(
                  exerciseJoin1.get("scenario").get("id"), exerciseJoin2.get("scenario").get("id")),
              // check this column is not null for joining
              cb.isNotNull(exerciseJoin1.get("launchOrder")),
              cb.isNotNull(exerciseJoin2.get("launchOrder")),
              // only consider finished simulations
              cb.equal(exerciseJoin1.get("status"), ExerciseStatus.FINISHED),
              cb.equal(exerciseJoin2.get("status"), ExerciseStatus.FINISHED),
              // trim to "latest" simulation
              cb.lessThan(exerciseJoin1.get("launchOrder"), exerciseJoin2.get("launchOrder"))));

      return cb.and(
          cb.isNull(exerciseJoin2.get("id")),
          cb.or(
              cb.equal(exerciseJoin1.get("status"), ExerciseStatus.FINISHED),
              cb.isNull(exerciseJoin1.get("id"))));
    };
  }
}
