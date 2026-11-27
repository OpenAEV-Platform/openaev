package io.openaev.database.specification;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingOccurrence;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
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

      // Grouped by the displayed value, not the cleartext: two secrets displayed the same way form
      // one row, so the number of rows never tells that their hidden parts differ (ADR-017).
      Predicate sameGroup =
          cb.and(
              cb.equal(other.get("type"), candidate.get("type")),
              cb.equal(other.get("valueDisplay"), candidate.get("valueDisplay")));
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

      // A row written by a pod of the previous version has no displayed value until the delayed
      // backfill fills it. NULL never equals NULL, so each such row would form its own group and
      // tell two secrets displayed alike apart: it is left out, as it is from value search.
      Predicate candidateWhere =
          cb.and(cb.isNotNull(candidate.get("valueDisplay")), cb.not(cb.exists(newerSibling)));
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

  /**
   * Every occurrence, with its assets, of the given (type, displayed value) groups.
   *
   * @param types the types of the groups
   * @param displayValues the displayed values of the groups, as stored in {@code
   *     finding_value_display}
   * @param specification the scope the occurrences must belong to
   */
  public static Specification<Finding> findAllWithAssetsByTypeDisplayValueIn(
      List<ContractOutputType> types,
      List<String> displayValues,
      Specification<Finding> specification) {
    return Specification.<Finding>unrestricted()
        .and(specification)
        .and(withAssets())
        .and(
            (root, query, cb) -> {
              Predicate typeIn = root.get("type").in(types);
              Predicate valueIn = root.get("valueDisplay").in(displayValues);
              return cb.and(typeIn, valueIn);
            });
  }
}
