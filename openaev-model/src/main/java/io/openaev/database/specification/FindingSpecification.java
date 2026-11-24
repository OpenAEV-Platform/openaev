package io.openaev.database.specification;

import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingOccurrence;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.util.function.BiFunction;
import org.springframework.data.jpa.domain.Specification;

public class FindingSpecification {

  private FindingSpecification() {}

  public static Specification<Finding> findFindingsForInject(@NotNull final String injectId) {
    return hasOccurrence(
        (occurrence, cb) -> cb.equal(occurrence.get("inject").get("id"), injectId));
  }

  public static Specification<Finding> findFindingsForSimulation(
      @NotNull final String simulationId) {
    return hasOccurrence(
        (occurrence, cb) ->
            cb.equal(occurrence.get("inject").get("exercise").get("id"), simulationId));
  }

  public static Specification<Finding> findFindingsForScenario(@NotNull final String scenarioId) {
    return hasOccurrence(
        (occurrence, cb) ->
            cb.equal(
                occurrence.get("inject").get("exercise").get("scenario").get("id"), scenarioId));
  }

  /**
   * Applies the search filters and the text search in a subquery. They can go through a to-many
   * path (occurrences, assets, asset groups): joined on the root, a finding matching through
   * several rows would come back once per row, and be counted as many times.
   */
  public static Specification<Finding> matching(Specification<Finding> filters) {
    return (root, query, cb) -> {
      Subquery<String> matches = query.subquery(String.class);
      Root<Finding> candidate = matches.from(Finding.class);
      Predicate predicate = filters.toPredicate(candidate, query, cb);
      if (predicate == null) {
        return null;
      }
      matches.select(candidate.get("id")).where(predicate);
      return root.get("id").in(matches);
    };
  }

  // EXISTS rather than a join: a finding with several occurrences in the scope stays one row.
  private static Specification<Finding> hasOccurrence(
      BiFunction<Root<FindingOccurrence>, CriteriaBuilder, Predicate> condition) {
    return (root, query, cb) -> {
      Subquery<Integer> subquery = query.subquery(Integer.class);
      Root<FindingOccurrence> occurrence = subquery.from(FindingOccurrence.class);
      subquery
          .select(cb.literal(1))
          .where(cb.equal(occurrence.get("finding"), root), condition.apply(occurrence, cb));
      return cb.exists(subquery);
    };
  }

  public static Specification<Finding> findFindingsForEndpoint(@NotNull final String endpointId) {
    return (root, query, cb) -> cb.equal(root.get("assets").get("id"), endpointId);
  }
}
