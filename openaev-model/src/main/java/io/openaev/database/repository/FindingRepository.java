package io.openaev.database.repository;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.raw.RawFindingIndexing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
      "SELECT COUNT(DISTINCT ag.id) FROM Finding f JOIN f.inject i JOIN i.assetGroups ag"
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
  List<Finding> findAllByInjectId(@NotNull final String injectId);

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
          + " WHERE f.inject.id = :injectId AND f.tenant.id = :tenantId")
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
          + " WHERE f.id = :findingId AND f.inject.id = :injectId AND f.tenant.id = :tenantId")
  Optional<Finding> findByIdAndInjectIdAndTenantId(
      @NotBlank @Param("findingId") String findingId,
      @NotBlank @Param("injectId") String injectId,
      @NotBlank @Param("tenantId") String tenantId);

  // For testing purposes only
  @Query(
      value =
          "SELECT f FROM Finding f WHERE f.inject.id = :injectId AND f.value = :value AND f.type = :type AND f.field = :key")
  Optional<Finding> findByInjectIdAndValueAndTypeAndKey(
      @NotBlank @Param("injectId") String injectId,
      @NotBlank @Param("value") String value,
      @NotNull @Param("type") ContractOutputType type,
      @NotBlank @Param("key") String key);

  // -- INDEXING --

  // One row PER FINDING: assets are aggregated. The previous per-(finding, asset) rows shared the
  // same base_id, so multi-asset findings kept only one arbitrary asset in the search index (each
  // bulk upsert overwrote the previous row) and the LIMIT applied to joined rows, not findings.
  @Query(
      value =
          "SELECT f.finding_id, f.finding_value, f.finding_type, f.finding_field,"
              + " f.finding_inject_id, i.inject_exercise, MAX(se.scenario_id) AS scenario_id,"
              + " array_agg(DISTINCT fa.asset_id) FILTER ( WHERE fa.asset_id IS NOT NULL ) AS asset_ids,"
              + " f.finding_created_at, f.finding_updated_at, f.tenant_id "
              + "FROM findings f "
              + "LEFT JOIN injects i ON i.inject_id = f.finding_inject_id "
              + "LEFT JOIN scenarios_exercises se ON i.inject_exercise = se.exercise_id "
              + "LEFT JOIN findings_assets fa ON f.finding_id = fa.finding_id "
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
              // of degrading. MAX(...) and array_agg(...) stay out, being aggregates.
              + "GROUP BY f.finding_id, f.finding_value, f.finding_type, f.finding_field,"
              + " f.finding_inject_id, i.inject_exercise, f.finding_created_at,"
              + " f.finding_updated_at, f.tenant_id "
              + "ORDER BY f.finding_updated_at, f.finding_id LIMIT :limit;",
      nativeQuery = true)
  List<RawFindingIndexing> findForIndexing(
      @Param("from") Instant from, @Param("fromId") String fromId, @Param("limit") int limit);

  // The finding upsert, its asset link and its tag links used to be one modifying CTE
  // (WITH ... INSERT ... RETURNING), which JSQLParser cannot parse, so the tenant inspector would
  // fail-close on it. They are split into three parseable statements run together in one
  // transaction by FindingWriter (REQUIRES_NEW). The transaction boundary lives in the API layer,
  // not here.
  //
  // The conflict branch MUST bump finding_updated_at: search indexing is driven by an updated_at
  // cursor (findForIndexing), so a re-detected finding (same natural key, possibly a new asset
  // link added right after by insertFindingAsset) would otherwise never be re-indexed and its
  // ES document would keep a stale asset list forever.
  //
  // This native write skips the JPA callbacks of Finding, so the caller passes the displayed value
  // computed by Finding.displayValueOf. The conflict branch writes it again, so a row written
  // before the column existed gets it the next time the finding is detected.

  @Query(
      value =
          """
        INSERT INTO findings
          (finding_id, finding_field, finding_type, finding_value, finding_value_display,
           finding_labels, finding_inject_id, finding_name, tenant_id)
        VALUES
          (gen_random_uuid(), :findingField, :findingType, :findingValue, :findingValueDisplay,
           :findingLabels, :findingInjectId, :findingName, :tenantId)
        ON CONFLICT (finding_inject_id, finding_field, finding_type, finding_value)
        DO UPDATE SET finding_name = EXCLUDED.finding_name,
          finding_value_display = EXCLUDED.finding_value_display,
          finding_updated_at = now()
        RETURNING finding_id
        """,
      nativeQuery = true)
  String upsertFinding(
      @Param("findingField") String findingField,
      @Param("findingType") String findingType,
      @Param("findingValue") String findingValue,
      @Param("findingValueDisplay") String findingValueDisplay,
      @Param("findingLabels") String[] findingLabels,
      @Param("findingInjectId") String injectId,
      @Param("findingName") String name,
      @Param("tenantId") String tenantId);

  @Modifying
  @Query(
      value =
          "INSERT INTO findings_assets (finding_id, asset_id) VALUES (:findingId, :assetId)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void insertFindingAsset(@Param("findingId") String findingId, @Param("assetId") String assetId);

  @Modifying
  @Query(
      value =
          "INSERT INTO findings_tags (finding_id, tag_id)"
              + " SELECT :findingId, tag_id FROM unnest(CAST(:tagIds AS varchar[])) AS tag_id"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void insertFindingTags(@Param("findingId") String findingId, @Param("tagIds") String[] tagIds);
}
