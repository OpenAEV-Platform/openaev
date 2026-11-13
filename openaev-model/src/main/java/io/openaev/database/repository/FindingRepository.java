package io.openaev.database.repository;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.raw.RawFindingIndexing;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FindingRepository
    extends CrudRepository<Finding, String>, JpaSpecificationExecutor<Finding> {

  Optional<Finding> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  boolean existsByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  // -- GROUP-WIDE SUMMARY (findings deduplicated by (type, displayed value) within a tenant) --
  //
  // Grouped on the displayed value like the distinct list, so the summary of a row counts the same
  // occurrences as the row itself, and never tells two secrets displayed alike apart (ADR-017).

  /**
   * First/last seen and occurrence count for a (type, value) finding group. A single {@code
   * Finding} row is one occurrence per inject, so the true "first seen" is the MIN of the group's
   * creation dates and "last seen" the MAX of the group's update dates - not the dates of an
   * arbitrary representative row.
   */
  @Query(
      "SELECT MIN(f.creationDate) AS firstSeen, MAX(f.updateDate) AS lastSeen,"
          + " COUNT(f) AS occurrences FROM Finding f"
          + " WHERE f.type = :type AND f.valueDisplay = :displayValue AND f.tenant.id = :tenantId")
  FindingSeenAggregate findSeenAggregate(
      @Param("type") ContractOutputType type,
      @Param("displayValue") String displayValue,
      @Param("tenantId") String tenantId);

  @Query(
      "SELECT COUNT(DISTINCT a.id) FROM Finding f JOIN f.assets a"
          + " WHERE f.type = :type AND f.valueDisplay = :displayValue AND f.tenant.id = :tenantId")
  long countDistinctAssets(
      @Param("type") ContractOutputType type,
      @Param("displayValue") String displayValue,
      @Param("tenantId") String tenantId);

  @Query(
      "SELECT COUNT(DISTINCT t.id) FROM Finding f JOIN f.teams t"
          + " WHERE f.type = :type AND f.valueDisplay = :displayValue AND f.tenant.id = :tenantId")
  long countDistinctTeams(
      @Param("type") ContractOutputType type,
      @Param("displayValue") String displayValue,
      @Param("tenantId") String tenantId);

  @Query(
      "SELECT COUNT(DISTINCT u.id) FROM Finding f JOIN f.users u"
          + " WHERE f.type = :type AND f.valueDisplay = :displayValue AND f.tenant.id = :tenantId")
  long countDistinctUsers(
      @Param("type") ContractOutputType type,
      @Param("displayValue") String displayValue,
      @Param("tenantId") String tenantId);

  @Query(
      "SELECT COUNT(DISTINCT ag.id) FROM FindingOccurrence o JOIN o.finding f JOIN o.inject i"
          + " JOIN i.assetGroups ag"
          + " WHERE f.type = :type AND f.valueDisplay = :displayValue AND f.tenant.id = :tenantId")
  long countDistinctAssetGroups(
      @Param("type") ContractOutputType type,
      @Param("displayValue") String displayValue,
      @Param("tenantId") String tenantId);

  /** Projection for {@link #findSeenAggregate}. */
  interface FindingSeenAggregate {
    Instant getFirstSeen();

    Instant getLastSeen();

    long getOccurrences();
  }

  // For testing purposes only
  @Query(
      "SELECT f FROM Finding f WHERE EXISTS"
          + " (SELECT 1 FROM FindingOccurrence o WHERE o.finding = f AND o.inject.id = :injectId)")
  List<Finding> findAllByInjectId(@NotNull @Param("injectId") final String injectId);

  /**
   * An inject's findings, scoped explicitly to a tenant, with their assets fetch-joined. Used off
   * the request thread (the attack-path findings copy): the scope is a query predicate rather than
   * the incidental uniqueness of the inject id (the ambient {@code @Filter tenantFilter} is not
   * enabled there), and the fetch-join preloads {@code assets} so the copy's per-finding {@code
   * getAssets()} does not trigger an N+1 read on a large run. {@code DISTINCT} collapses the join
   * fan-out.
   */
  @Query(
      "SELECT DISTINCT f FROM Finding f LEFT JOIN FETCH f.assets"
          + " WHERE f.tenant.id = :tenantId"
          + " AND EXISTS (SELECT 1 FROM FindingOccurrence o WHERE o.finding = f AND o.inject.id = :injectId)")
  List<Finding> findAllByInjectIdAndTenantId(
      @NotBlank @Param("injectId") String injectId, @NotBlank @Param("tenantId") String tenantId);

  /**
   * One of an inject's findings, scoped explicitly to a tenant, with its assets fetch-joined. Same
   * shape as {@link #findAllByInjectIdAndTenantId} for the attack-path copy of a single finding (a
   * phishing credentials submission): the inject and tenant predicates keep the scope explicit off
   * the request thread, and the fetch-join preloads {@code assets} read by the copy.
   */
  @Query(
      "SELECT DISTINCT f FROM Finding f LEFT JOIN FETCH f.assets"
          + " WHERE f.id = :findingId AND f.tenant.id = :tenantId"
          + " AND EXISTS (SELECT 1 FROM FindingOccurrence o WHERE o.finding = f AND o.inject.id = :injectId)")
  Optional<Finding> findByIdAndInjectIdAndTenantId(
      @NotBlank @Param("findingId") String findingId,
      @NotBlank @Param("injectId") String injectId,
      @NotBlank @Param("tenantId") String tenantId);

  // For testing purposes only
  @Query(
      value =
          "SELECT f FROM Finding f WHERE f.value = :value AND f.type = :type"
              + " AND EXISTS (SELECT 1 FROM FindingOccurrence o WHERE o.finding = f"
              + " AND o.inject.id = :injectId AND o.field = :key)")
  Optional<Finding> findByInjectIdAndValueAndTypeAndKey(
      @NotBlank @Param("injectId") String injectId,
      @NotBlank @Param("value") String value,
      @NotNull @Param("type") ContractOutputType type,
      @NotBlank @Param("key") String key);

  // -- DISPLAYED VALUE CATCH-UP --
  //
  // During a rolling deploy, pods of the previous version insert findings without
  // finding_value_display. FindingValueDisplayBackfill fills them once, after the old pods are
  // gone, with these two queries, in TxCtx.allTenants().
  //
  // Native: a deliberate cross-tenant maintenance sweep on a column the entity never lets JPA write
  // directly. No side effect is lost: the search index and the stream already carry this masked
  // value, computed on the fly while the column was empty.

  /** Findings without a displayed value, after a primary key cursor, so a pass reads each once. */
  @Query(
      value =
          "SELECT f.finding_id AS id, f.finding_type AS type, f.finding_value AS value"
              + " FROM findings f WHERE f.finding_id > :afterId AND f.finding_value_display IS NULL"
              + " ORDER BY f.finding_id LIMIT :limit",
      nativeQuery = true)
  List<FindingWithoutValueDisplay> findWithoutValueDisplayAfter(
      @Param("afterId") String afterId, @Param("limit") int limit);

  /** Fills the displayed value of one finding, unless another writer filled it first. */
  @Modifying
  @Query(
      value =
          "UPDATE findings SET finding_value_display = :valueDisplay"
              + " WHERE finding_id = :findingId AND finding_value_display IS NULL",
      nativeQuery = true)
  int fillValueDisplay(
      @Param("findingId") String findingId, @Param("valueDisplay") String valueDisplay);

  /** Projection for {@link #findWithoutValueDisplayAfter}. */
  interface FindingWithoutValueDisplay {
    String getId();

    String getType();

    String getValue();
  }

  // -- INDEXING --

  // One row PER FINDING: assets, and the injects, simulations, scenarios and fields of its
  // occurrences, are aggregated. The previous per-(finding, asset) rows shared the same base_id, so
  // multi-asset findings kept only one arbitrary asset in the search index (each bulk upsert
  // overwrote the previous row) and the LIMIT applied to joined rows, not findings.
  // Native: array_agg over the occurrences and assets. Read only, no side effect.
  @Query(
      value =
          "SELECT f.finding_id, f.finding_value, f.finding_value_display, f.finding_type,"
              + " array_agg(DISTINCT fo.finding_occurrence_field)"
              + " FILTER ( WHERE fo.finding_occurrence_field IS NOT NULL ) AS finding_fields,"
              + " array_agg(DISTINCT fo.finding_occurrence_inject_id)"
              + " FILTER ( WHERE fo.finding_occurrence_inject_id IS NOT NULL ) AS inject_ids,"
              + " array_agg(DISTINCT i.inject_exercise)"
              + " FILTER ( WHERE i.inject_exercise IS NOT NULL ) AS simulation_ids,"
              + " array_agg(DISTINCT se.scenario_id)"
              + " FILTER ( WHERE se.scenario_id IS NOT NULL ) AS scenario_ids,"
              + " fl.finding_location_asset_id, fl.finding_location_user_id,"
              + " f.finding_created_at, f.finding_updated_at, f.tenant_id "
              + "FROM findings f "
              + "LEFT JOIN finding_occurrences fo ON fo.finding_occurrence_finding_id = f.finding_id "
              + "LEFT JOIN injects i ON i.inject_id = fo.finding_occurrence_inject_id "
              + "LEFT JOIN scenarios_exercises se ON i.inject_exercise = se.exercise_id "
              + "LEFT JOIN finding_locations fl ON fl.finding_location_id = f.finding_location_id "
              // Keyset resume on (updated_at, id): a batch whose LIMIT falls inside a group of
              // findings sharing one timestamp - an upsert stamps a whole transaction with now() -
              // resumes after its last id instead of skipping the rest of the group. A null id
              // (first round, grace-window cap) re-serves the boundary group, idempotently.
              + "WHERE (f.finding_updated_at > :from OR (f.finding_updated_at = :from"
              + " AND (CAST(:fromId AS text) IS NULL OR f.finding_id > CAST(:fromId AS text)))) "
              // Every non-aggregated projected column, not just the id. Grouping on the id
              // alone relies on PostgreSQL's functional-dependency rule, which only applies to
              // BASE TABLES. Once findings is v2-active the tenant statement inspector rewrites
              // "FROM findings f" into a derived table, the dependency can no longer be inferred,
              // and this query stops being valid SQL: search indexing then fails outright instead
              // of degrading. The array_agg(...) stay out, being aggregates.
              + "GROUP BY f.finding_id, f.finding_value, f.finding_value_display, f.finding_type,"
              + " fl.finding_location_asset_id, fl.finding_location_user_id,"
              + " f.finding_created_at, f.finding_updated_at, f.tenant_id "
              + "ORDER BY f.finding_updated_at, f.finding_id LIMIT :limit;",
      nativeQuery = true)
  List<RawFindingIndexing> findForIndexing(
      @Param("from") Instant from, @Param("fromId") String fromId, @Param("limit") int limit);

  // -- READ ON THE IDENTITY KEY --

  // Over-matches (any type x any hash x any location): the caller keeps the exact identities.
  // Locked in id order: concurrent batches refresh a finding one after the other, without deadlock
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT f FROM Finding f WHERE f.tenant.id = :tenantId AND f.type IN :types"
          + " AND f.valueHash IN :hashes AND f.location.id IN :locationIds ORDER BY f.id")
  List<Finding> findAllByTenantTypeValueHashAndLocationForUpdate(
      @Param("tenantId") String tenantId,
      @Param("types") Collection<ContractOutputType> types,
      @Param("hashes") Collection<String> hashes,
      @Param("locationIds") Collection<String> locationIds);
}
