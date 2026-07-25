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

  // For testing purposes only
  List<Finding> findAllByInjectId(@NotNull final String injectId);

  /**
   * An inject's findings, scoped explicitly to a tenant. Used off the request thread (the
   * attack-path findings copy), where the ambient {@code @Filter tenantFilter} is not enabled, so
   * the scope must be a query predicate rather than the incidental uniqueness of the inject id.
   */
  @Query("SELECT f FROM Finding f WHERE f.inject.id = :injectId AND f.tenant.id = :tenantId")
  List<Finding> findAllByInjectIdAndTenantId(
      @NotBlank @Param("injectId") String injectId, @NotBlank @Param("tenantId") String tenantId);

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
              + "WHERE f.finding_updated_at > :from "
              + "GROUP BY f.finding_id, i.inject_exercise "
              + "ORDER BY f.finding_updated_at LIMIT :limit;",
      nativeQuery = true)
  List<RawFindingIndexing> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);

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

  @Query(
      value =
          """
        INSERT INTO findings
          (finding_id, finding_field, finding_type, finding_value,
           finding_labels, finding_inject_id, finding_name, tenant_id)
        VALUES
          (gen_random_uuid(), :findingField, :findingType, :findingValue,
           :findingLabels, :findingInjectId, :findingName, :tenantId)
        ON CONFLICT (finding_inject_id, finding_field, finding_type, finding_value)
        DO UPDATE SET finding_name = EXCLUDED.finding_name, finding_updated_at = now()
        RETURNING finding_id
        """,
      nativeQuery = true)
  String upsertFinding(
      @Param("findingField") String findingField,
      @Param("findingType") String findingType,
      @Param("findingValue") String findingValue,
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
