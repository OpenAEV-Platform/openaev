package io.openaev.database.repository;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.raw.RawFindingIndexing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
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

  /** The distinct assets of every finding sharing this (type, displayed value) in the tenant. */
  @Query(
      "SELECT DISTINCT a.id FROM Finding f JOIN f.assets a"
          + " WHERE f.type = :type AND f.valueDisplay = :displayValue AND f.tenant.id = :tenantId")
  List<String> findDistinctAssetIds(
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

  // -- KEEP ON DELETE (opt-in keep_findings on the delete endpoints) --
  //
  // By default deleting an inject, simulation or scenario deletes the findings its injects produced
  // (finding_inject_id_fk is ON DELETE CASCADE). On request, these updates detach the findings
  // first, so they survive: their occurrences keep the history (finding_occurrence_inject_id is ON
  // DELETE SET NULL). finding_updated_at is bumped so the search index re-indexes them without the
  // deleted inject, simulation or scenario in their dependencies. The session is cleared so an
  // inject already loaded does not cascade-delete a finding it no longer owns.

  /** Detaches the findings of the given injects. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE findings SET finding_inject_id = NULL, finding_updated_at = now()"
              + " WHERE finding_inject_id IN (:injectIds)",
      nativeQuery = true)
  int detachFromInjects(@Param("injectIds") List<String> injectIds);

  /** Detaches the findings of the injects of the given simulations. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE findings SET finding_inject_id = NULL, finding_updated_at = now()"
              + " WHERE finding_inject_id IN (SELECT inject_id FROM injects"
              + " WHERE inject_exercise IN (:simulationIds))",
      nativeQuery = true)
  int detachFromSimulations(@Param("simulationIds") List<String> simulationIds);

  /** Detaches the findings of the injects of the given scenarios. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE findings SET finding_inject_id = NULL, finding_updated_at = now()"
              + " WHERE finding_inject_id IN (SELECT inject_id FROM injects"
              + " WHERE inject_scenario IN (:scenarioIds))",
      nativeQuery = true)
  int detachFromScenarios(@Param("scenarioIds") List<String> scenarioIds);

  /** Projection for {@link #findWithoutValueDisplayAfter}. */
  interface FindingWithoutValueDisplay {
    String getId();

    String getType();

    String getValue();
  }

  // -- INDEXING --

  // One row PER FINDING: assets, and the injects, simulations, scenarios, asset groups and fields
  // of its occurrences, are aggregated. The previous per-(finding, asset) rows shared the same
  // base_id, so multi-asset findings kept only one arbitrary asset in the search index (each bulk
  // upsert overwrote the previous row).
  //
  // Each array comes from its own correlated sub-query, and the outer query joins nothing that
  // fans out. Joining every collection into one GROUP BY multiplied them (occurrences x scenarios
  // x assets x asset groups rows per finding), and the LIMIT only applied once every candidate
  // finding was joined and aggregated: during a re-feed from epoch, that is the whole table on
  // every batch (30-60 s per batch of 500 once injects_asset_groups was added). Here the LIMIT
  // takes the next findings off the cursor index and the sub-queries only run for those.
  // With no GROUP BY, the query also stays valid once the tenant statement inspector rewrites
  // "FROM findings f" into a derived table.
  // Native: array_agg in correlated sub-queries. Read only, no side effect.
  @Query(
      value =
          "SELECT f.finding_id, f.finding_value, f.finding_value_display, f.finding_type,"
              + " (SELECT array_agg(DISTINCT fo.finding_occurrence_field)"
              + " FROM finding_occurrences fo"
              + " WHERE fo.finding_occurrence_finding_id = f.finding_id"
              + " AND fo.finding_occurrence_field IS NOT NULL) AS finding_fields,"
              + " (SELECT array_agg(DISTINCT fo.finding_occurrence_inject_id)"
              + " FROM finding_occurrences fo"
              + " WHERE fo.finding_occurrence_finding_id = f.finding_id"
              + " AND fo.finding_occurrence_inject_id IS NOT NULL) AS inject_ids,"
              + " (SELECT array_agg(DISTINCT i.inject_exercise)"
              + " FROM finding_occurrences fo"
              + " JOIN injects i ON i.inject_id = fo.finding_occurrence_inject_id"
              + " WHERE fo.finding_occurrence_finding_id = f.finding_id"
              + " AND i.inject_exercise IS NOT NULL) AS simulation_ids,"
              + " (SELECT array_agg(DISTINCT se.scenario_id)"
              + " FROM finding_occurrences fo"
              + " JOIN injects i ON i.inject_id = fo.finding_occurrence_inject_id"
              + " JOIN scenarios_exercises se ON se.exercise_id = i.inject_exercise"
              + " WHERE fo.finding_occurrence_finding_id = f.finding_id) AS scenario_ids,"
              + " (SELECT array_agg(fa.asset_id) FROM findings_assets fa"
              + " WHERE fa.finding_id = f.finding_id) AS asset_ids,"
              // The asset groups that statically contain one of the finding's assets. Never the
              // groups the inject targeted: those include groups the hit asset is not in. The
              // dynamic memberships are added by FindingHandler through the membership resolver.
              + " (SELECT array_agg(DISTINCT aga.asset_group_id)"
              + " FROM findings_assets fa"
              + " JOIN asset_groups_assets aga ON aga.asset_id = fa.asset_id"
              + " WHERE fa.finding_id = f.finding_id) AS asset_group_ids,"
              // The inject the finding is deleted with (ON DELETE CASCADE), and its context. One
              // row at most: injects is joined on its primary key.
              + " f.finding_inject_id, li.inject_exercise AS finding_simulation_id,"
              + " (SELECT MAX(lse.scenario_id) FROM scenarios_exercises lse"
              + " WHERE lse.exercise_id = li.inject_exercise) AS finding_scenario_id,"
              + " f.finding_created_at, f.finding_updated_at, f.tenant_id "
              + "FROM findings f "
              + "LEFT JOIN injects li ON li.inject_id = f.finding_inject_id "
              // Keyset resume on (updated_at, id): a batch whose LIMIT falls inside a group of
              // findings sharing one timestamp - an upsert stamps a whole transaction with now() -
              // resumes after its last id instead of skipping the rest of the group. A null id
              // (first round, grace-window cap) re-serves the boundary group, idempotently.
              + "WHERE (f.finding_updated_at > :from OR (f.finding_updated_at = :from"
              + " AND (CAST(:fromId AS text) IS NULL OR f.finding_id > CAST(:fromId AS text)))) "
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
  // computed by SensitiveValueMaskingUtils.maskIfNeededFailClosed. The conflict branch writes it
  // again, so a row written before the column existed gets it the next time the finding is
  // detected.
  //
  // Being native, the upsert and the asset link skip ModelBaseListener: FindingWriter publishes
  // the lifecycle event itself, which notification triggers and the stream depend on. "inserted"
  // tells a create from an update: xmax is 0 only on a row version this statement inserted, the
  // conflict branch stamps it with its own transaction id. insertFindingAsset returns 0 when the
  // link already existed, which tells a new asset from a repeated report.

  /** Projection for {@link #upsertFinding}. */
  interface UpsertedFinding {
    String getId();

    boolean getInserted();
  }

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
        RETURNING finding_id AS id, (xmax = 0) AS inserted
        """,
      nativeQuery = true)
  UpsertedFinding upsertFinding(
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
  int insertFindingAsset(@Param("findingId") String findingId, @Param("assetId") String assetId);

  @Modifying
  @Query(
      value =
          "INSERT INTO findings_tags (finding_id, tag_id)"
              + " SELECT :findingId, tag_id FROM unnest(CAST(:tagIds AS varchar[])) AS tag_id"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  void insertFindingTags(@Param("findingId") String findingId, @Param("tagIds") String[] tagIds);

  // -- COUNTERPART FACTS (the key facts another XTM product shows next to its pivot) --

  /** Findings recorded on each of the given assets, one row per asset holding at least one. */
  @Query(
      "SELECT a.id AS objectKey, COUNT(DISTINCT f.id) AS findings, 0L AS assets"
          + " FROM Finding f JOIN f.assets a WHERE a.id IN :assetIds GROUP BY a.id")
  List<FindingCountRow> countFindingsByAssetIdIn(@Param("assetIds") Collection<String> assetIds);

  /**
   * Findings of a type whose displayed value is one of the given values (compared upper-case, as
   * CVE ids are), with the number of distinct assets they were found on; one row per value found.
   * It reads the displayed value, never the cleartext one (ADR-017): a CVE id is displayed as
   * stored.
   */
  @Query(
      "SELECT UPPER(f.valueDisplay) AS objectKey, COUNT(DISTINCT f.id) AS findings,"
          + " COUNT(DISTINCT a.id) AS assets FROM Finding f LEFT JOIN f.assets a"
          + " WHERE f.type = :type AND UPPER(f.valueDisplay) IN :values"
          + " GROUP BY UPPER(f.valueDisplay)")
  List<FindingCountRow> countFindingsByTypeAndValueIn(
      @Param("type") ContractOutputType type, @Param("values") Collection<String> values);

  interface FindingCountRow {
    String getObjectKey();

    long getFindings();

    long getAssets();
  }
}
