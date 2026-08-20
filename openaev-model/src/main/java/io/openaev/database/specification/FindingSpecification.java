package io.openaev.database.specification;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
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

  /**
   * Restricts the query to exactly one representative row per (type, value) group: the MOST RECENT
   * occurrence, i.e. the one with the greatest {@code updateDate}, tie-broken by the smallest
   * {@code id} for determinism. Selecting the most recent occurrence (instead of an arbitrary
   * MIN(id)) is what fixes issue #7273: the row's detail link opens the latest occurrence, and
   * because the representative's own {@code updateDate} now equals the group's max, the outer
   * query's ORDER BY / pagination on {@code finding_updated_at} ("Last seen") matches the displayed
   * group value.
   *
   * <p>Group membership honours {@code baseSpec}: it is applied to the candidate rows AND
   * re-applied inside the "is there a newer sibling?" check, so the representative is the most
   * recent occurrence <em>among the occurrences matching the filter</em>. A filter that matches
   * only an older occurrence therefore makes that occurrence the representative rather than making
   * the group vanish.
   */
  public static Specification<Finding> distinctTypeValueWithFilter(
      Specification<Finding> baseSpec) {
    return (root, query, cb) -> {
      query.distinct(true);

      // Candidate representative rows: one per (type, value) group is the answer.
      Subquery<String> representatives = query.subquery(String.class);
      Root<Finding> candidate = representatives.from(Finding.class);

      // A strictly-more-recent sibling in the same group: greater updateDate, or the same
      // updateDate with a smaller id (the tie-break that guarantees a single representative).
      Subquery<String> newerSibling = representatives.subquery(String.class);
      Root<Finding> other = newerSibling.from(Finding.class);
      newerSibling.select(other.get("id"));

      Predicate sameGroup =
          cb.and(
              cb.equal(other.get("type"), candidate.get("type")),
              cb.equal(other.get("value"), candidate.get("value")));
      Predicate strictlyNewer =
          cb.or(
              cb.greaterThan(
                  other.<Instant>get("updateDate"), candidate.<Instant>get("updateDate")),
              cb.and(
                  cb.equal(other.get("updateDate"), candidate.get("updateDate")),
                  cb.lessThan(other.<String>get("id"), candidate.<String>get("id"))));
      Predicate newerWhere = cb.and(sameGroup, strictlyNewer);
      if (baseSpec != null) {
        Predicate otherSpec = baseSpec.toPredicate(other, query, cb);
        if (otherSpec != null) {
          newerWhere = cb.and(newerWhere, otherSpec);
        }
      }
      newerSibling.where(newerWhere);

      Predicate candidateWhere = cb.not(cb.exists(newerSibling));
      if (baseSpec != null) {
        Predicate candidateSpec = baseSpec.toPredicate(candidate, query, cb);
        if (candidateSpec != null) {
          candidateWhere = cb.and(candidateSpec, candidateWhere);
        }
      }
      representatives.select(candidate.get("id"));
      representatives.where(candidateWhere);

      return root.get("id").in(representatives);
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
    return Specification.<Finding>unrestricted()
        .and(specification)
        .and(withAssets())
        .and(
            (root, query, cb) -> {
              Predicate typeIn = root.get("type").in(types);
              Predicate valueIn = root.get("value").in(values);
              return cb.and(typeIn, valueIn);
            });
  }
}
