package io.openaev.database.repository;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.raw.RawFinding;
import io.openaev.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface FindingRepository
    extends CrudRepository<Finding, String>, JpaSpecificationExecutor<Finding> {

  List<Finding> findAllByInjectId(@NotNull final String injectId);

  @Query(
      value =
          "SELECT f FROM Finding f WHERE f.inject.id = :injectId AND f.value = :value AND f.type = :type AND f.field = :key")
  Optional<Finding> findByInjectIdAndValueAndTypeAndKey(
      @NotBlank @Param("injectId") String injectId,
      @NotBlank @Param("value") String value,
      @NotNull @Param("type") ContractOutputType type,
      @NotBlank @Param("key") String key);

  // -- INDEXING --

  @Query(
      value =
          "SELECT f.finding_id, f.finding_value, f.finding_type, f.finding_field,"
              + " f.finding_inject_id, i.inject_exercise, se.scenario_id, fa.asset_id, f.finding_created_at, f.finding_updated_at "
              + "FROM findings f "
              + "LEFT JOIN injects i ON i.inject_id = f.finding_inject_id "
              + "LEFT JOIN scenarios_exercises se ON i.inject_exercise = se.exercise_id "
              + "LEFT JOIN findings_assets fa ON f.finding_id = fa.finding_id "
              + "WHERE f.finding_updated_at > :from ORDER BY f.finding_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawFinding> findForIndexing(@Param("from") Instant from);

  @Query(
      value =
          """
        WITH inserted_finding AS (
          INSERT INTO findings
            (finding_id, finding_field, finding_type, finding_value,
             finding_labels, finding_inject_id, finding_name)
          VALUES
            (gen_random_uuid(), :findingField, :findingType, :findingValue,
             :findingLabels, :findingInjectId, :findingName)
          ON CONFLICT (finding_inject_id, finding_field, finding_type, finding_value)
          DO UPDATE SET finding_name = EXCLUDED.finding_name
          RETURNING finding_id
        ),
        inserted_asset AS (
          INSERT INTO findings_assets (finding_id, asset_id)
          SELECT finding_id, :assetId
          FROM inserted_finding
          ON CONFLICT DO NOTHING
        ),
        inserted_tags AS (
          INSERT INTO findings_tags (finding_id, tag_id)
          SELECT finding_id, tag_id
          FROM inserted_finding
          CROSS JOIN unnest(CAST(:tagIds AS varchar[])) AS tag_id
          ON CONFLICT DO NOTHING
        )
        SELECT finding_id FROM inserted_finding
        """,
      nativeQuery = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  String saveCompleteFinding(
      @Param("findingField") String findingField,
      @Param("findingType") String findingType,
      @Param("findingValue") String findingValue,
      @Param("findingLabels") String[] findingLabels,
      @Param("findingInjectId") String injectId,
      @Param("findingName") String name,
      @Param("assetId") String assetId,
      @Param("tagIds") String[] tagIds);
}
