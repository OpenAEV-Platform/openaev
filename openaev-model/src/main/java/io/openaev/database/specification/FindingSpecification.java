package io.openaev.database.specification;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Finding;
import jakarta.persistence.criteria.*;
import java.util.List;
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

  public static Specification<Finding> distinctTypeValueWithFilter(
      Specification<Finding> baseSpec) {
    return (root, query, cb) -> {
      query.distinct(true);

      Subquery<String> subquery = query.subquery(String.class);
      Root<Finding> subRoot = subquery.from(Finding.class);

      Predicate specPredicate = null;
      if (baseSpec != null) {
        specPredicate = baseSpec.toPredicate(subRoot, query, cb);
      }

      subquery.select(cb.least(subRoot.<String>get("id")));
      if (specPredicate != null) {
        subquery.where(specPredicate);
      }
      subquery.groupBy(subRoot.get("type"), subRoot.get("value"));

      return root.get("id").in(subquery);
    };
  }

  public static Specification<Finding> withAssets() {
    return (root, query, cb) -> {
      root.fetch("assets", JoinType.LEFT);
      query.distinct(true);
      return null;
    };
  }

  public static Specification<Finding> findAllWithAssetsByTypeValueIn(
      List<ContractOutputType> types, List<String> values, Specification<Finding> specification) {
    return Specification.where(specification)
        .and(withAssets())
        .and(
            (root, query, cb) -> {
              Predicate typeIn = root.get("type").in(types);
              Predicate valueIn = root.get("value").in(values);
              return cb.and(typeIn, valueIn);
            });
  }
}
