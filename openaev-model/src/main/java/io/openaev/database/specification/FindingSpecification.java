package io.openaev.database.specification;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

      // Correlated subquery: the most recent finding_updated_at within the same (type, value)
      // group as subRoot. Used below to restrict subRoot to only the row(s) that are the most
      // recently seen occurrence of that group, rather than picking an arbitrary row by minimum
      // id (an id has no guaranteed relationship to recency).
      Subquery<Instant> maxUpdatedAtSubquery = subquery.subquery(Instant.class);
      Root<Finding> maxRoot = maxUpdatedAtSubquery.from(Finding.class);
      Predicate maxSpecPredicate = null;
      if (baseSpec != null) {
        maxSpecPredicate = baseSpec.toPredicate(maxRoot, query, cb);
      }
      Predicate sameGroup =
          cb.and(
              cb.equal(maxRoot.get("type"), subRoot.get("type")),
              cb.equal(maxRoot.get("value"), subRoot.get("value")));
      maxUpdatedAtSubquery.select(cb.greatest(maxRoot.<Instant>get("updateDate")));
      maxUpdatedAtSubquery.where(
          maxSpecPredicate != null ? cb.and(sameGroup, maxSpecPredicate) : sameGroup);

      // Tie-break on the minimum id when several rows within a group share the exact same
      // finding_updated_at, so the picked representative stays deterministic.
      subquery.select(cb.least(subRoot.<String>get("id")));
      Predicate isMostRecent = cb.equal(subRoot.get("updateDate"), maxUpdatedAtSubquery);
      subquery.where(specPredicate != null ? cb.and(specPredicate, isMostRecent) : isMostRecent);
      subquery.groupBy(subRoot.get("type"), subRoot.get("value"));

      return root.get("id").in(subquery);
    };
  }

  /**
   * Filters findings by their effective archived status: a finding is archived either because it
   * was manually archived ({@code finding_archived_at} set via the bulk "Archive" action) or
   * because it has not been re-detected for more than {@code archiveDays} (tenant-configurable, see
   * {@code TenantSettingsService#findFindingArchiveDays}), mirroring the same computation
   * previously done client-side only (see FindingList.tsx history). Doing this in SQL - instead of
   * fetching every row and hiding archived ones in the UI - is what actually reduces the page's
   * load, since archived findings can vastly outnumber active ones over time.
   */
  public static Specification<Finding> withArchived(boolean archived, int archiveDays) {
    return (root, query, cb) -> {
      Instant cutoff = Instant.now().minus(archiveDays, ChronoUnit.DAYS);
      Predicate manuallyArchived = cb.isNotNull(root.get("archivedAt"));
      Predicate timedOut = cb.lessThan(root.<Instant>get("updateDate"), cutoff);
      Predicate isArchived = cb.or(manuallyArchived, timedOut);
      return archived ? isArchived : cb.not(isArchived);
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
