package io.openaev.database.repository;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.raw.RawAssetGroup;
import io.openaev.database.raw.RawAssetGroupDynamicFilter;
import io.openaev.database.raw.RawAssetGroupIndexing;
import io.openaev.utils.Constants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetGroupRepository
    extends CrudRepository<AssetGroup, String>,
        StatisticRepository,
        JpaSpecificationExecutor<AssetGroup> {

  @Override
  @Query(
      "select COUNT(DISTINCT ag) from Inject i "
          + "join i.assetGroups as ag "
          + "join i.exercise as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and i.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct ag) from AssetGroup ag where ag.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  Optional<AssetGroup> findByExternalReference(String externalReference);

  @Query(
      "SELECT ag FROM AssetGroup ag "
          + "WHERE ag.id IN (SELECT DISTINCT ag2.id FROM AssetGroup ag2 "
          + "JOIN ag2.injects i WHERE i.scenario.id = :scenarioId)")
  List<AssetGroup> findDistinctByInjectsScenarioId(String scenarioId);

  @Query(
      "SELECT ag FROM AssetGroup ag "
          + "WHERE ag.id IN (SELECT DISTINCT ag2.id FROM AssetGroup ag2 "
          + "JOIN ag2.injects i WHERE i.exercise.id = :simulationId)")
  List<AssetGroup> findDistinctByInjectsSimulationId(String simulationId);

  /**
   * Returns the raw asset group having the ids passed in parameter
   *
   * @param ids a list of ids
   * @return the list of raw asset group
   */
  @Query(
      value =
          "SELECT ag.asset_group_id, ag.asset_group_name, CAST(ag.asset_group_dynamic_filter as text),  "
              + "coalesce(array_agg(aga.asset_id) FILTER ( WHERE aga.asset_id IS NOT NULL ), '{}') asset_ids "
              + "FROM asset_groups ag "
              + "LEFT JOIN asset_groups_assets aga ON ag.asset_group_id = aga.asset_group_id "
              + "WHERE ag.asset_group_id IN :ids "
              + "GROUP BY ag.asset_group_id;",
      nativeQuery = true)
  List<RawAssetGroup> rawAssetGroupByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          "SELECT ag.asset_group_id, ag.asset_group_name, CAST(ag.asset_group_dynamic_filter as text), "
              + "coalesce(array_agg(aga.asset_id) FILTER ( WHERE aga.asset_id IS NOT NULL ), '{}') asset_ids "
              + "FROM asset_groups ag "
              + "LEFT JOIN injects_asset_groups iat ON ag.asset_group_id = iat.asset_group_id "
              + "LEFT JOIN asset_groups_assets aga ON aga.asset_group_id = ag.asset_group_id "
              + "WHERE iat.asset_group_id IN (:assetGroupIds) OR iat.inject_id IN (:injectIds) "
              + "GROUP BY ag.asset_group_id, ag.asset_group_name, CAST(ag.asset_group_dynamic_filter as text) ;",
      nativeQuery = true)
  Set<RawAssetGroup> rawByIdsOrInjectIds(
      @Param("assetGroupIds") Set<String> assetGroupIds, @Param("injectIds") Set<String> injectIds);

  // -- PAGINATION --

  @Query(
      value =
          "SELECT ag.asset_group_id as asset_group_id, "
              + "CAST(asset_group_dynamic_filter as text) as asset_group_dynamic_filter "
              + "FROM asset_groups ag "
              + "JOIN injects_asset_groups iat ON ag.asset_group_id = iat.asset_group_id "
              + "WHERE iat.inject_id = :injectId "
              + "AND ag.asset_group_dynamic_filter IS NOT NULL;",
      nativeQuery = true)
  List<RawAssetGroupDynamicFilter> rawDynamicFiltersByInjectId(@Param("injectId") String injectId);

  @Query(
      value =
          "SELECT ag.asset_group_id as asset_group_id, "
              + "CAST(asset_group_dynamic_filter as text) as asset_group_dynamic_filter "
              + "FROM asset_groups ag "
              + "JOIN injects_asset_groups iat ON ag.asset_group_id = iat.asset_group_id "
              + "WHERE iat.inject_id = :injectId "
              + "AND ag.asset_group_dynamic_filter IS NOT NULL "
              + "AND ag.asset_group_id IN :assetGroupIds ;",
      nativeQuery = true)
  List<RawAssetGroupDynamicFilter> rawDynamicFiltersByInjectIdAndAssetGroupIds(
      @Param("injectId") String injectId, @Param("assetGroupIds") List<String> assetGroupIds);

  @Query(
      value =
          "SELECT ag.asset_group_id as asset_group_id, "
              + "CAST(asset_group_dynamic_filter as text) as asset_group_dynamic_filter "
              + "FROM asset_groups ag "
              + "WHERE ag.asset_group_dynamic_filter IS NOT NULL "
              + "AND ag.asset_group_id IN :assetGroupIds ;",
      nativeQuery = true)
  List<RawAssetGroupDynamicFilter> rawDynamicFiltersByAssetGroupIds(
      @Param("assetGroupIds") List<String> assetGroupIds);

  @Query(
      value =
          "SELECT ag.asset_group_id as asset_group_id, "
              + "CAST(asset_group_dynamic_filter as text) as asset_group_dynamic_filter "
              + "FROM asset_groups ag "
              + "JOIN injects_asset_groups iat ON ag.asset_group_id = iat.asset_group_id "
              + "WHERE iat.inject_id = :injectId "
              + "AND ag.asset_group_dynamic_filter IS NOT NULL "
              + "AND ag.asset_group_id NOT IN :assetGroupIds ;",
      nativeQuery = true)
  List<RawAssetGroupDynamicFilter> rawDynamicFiltersByInjectIdAndNotAssetGroupIds(
      @Param("injectId") String injectId, @Param("assetGroupIds") List<String> assetGroupIds);

  @NotNull
  @EntityGraph(value = "AssetGroup.tags-assets", type = EntityGraph.EntityGraphType.LOAD)
  Page<AssetGroup> findAll(@NotNull Specification<AssetGroup> spec, @NotNull Pageable pageable);

  @Query(
      value =
          "SELECT DISTINCT i.inject_exercise, ag.asset_group_id, ag.asset_group_name "
              + "FROM asset_groups ag "
              + "INNER JOIN injects_asset_groups iag ON ag.asset_group_id = iag.asset_group_id "
              + "INNER JOIN injects i ON iag.inject_id = i.inject_id "
              + "WHERE i.inject_exercise in :exerciseIds",
      nativeQuery = true)
  List<Object[]> assetGroupsByExerciseIds(Set<String> exerciseIds);

  @Query(
      value =
          "SELECT DISTINCT iag.inject_id, ag.asset_group_id, ag.asset_group_name "
              + "FROM asset_groups ag "
              + "INNER JOIN injects_asset_groups iag ON ag.asset_group_id = iag.asset_group_id "
              + "WHERE iag.inject_id in :injectIds",
      nativeQuery = true)
  List<Object[]> assetGroupsByInjectIds(Set<String> injectIds);

  @Query(
      "SELECT ag FROM Inject i"
          + " JOIN i.assetGroups ag"
          + " WHERE ("
          + "   :simulationOrScenarioId is NULL AND i.exercise.id is NULL AND i.scenario.id IS NULL"
          + "   OR (i.exercise.id = :simulationOrScenarioId"
          + "   OR i.scenario.id = :simulationOrScenarioId)"
          + " ) AND (:name IS NULL OR lower(ag.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<AssetGroup> findAllBySimulationOrScenarioIdAndName(
      String simulationOrScenarioId, String name);

  @Query(
      value =
          "SELECT ag.* "
              + "FROM asset_groups ag "
              + "INNER JOIN injects_asset_groups iag ON ag.asset_group_id = iag.asset_group_id",
      nativeQuery = true)
  List<AssetGroup> findAllAssetGroupsForAtomicTestingsSimulationsAndScenarios();

  @Query(
      value =
          """
    SELECT DISTINCT ag.asset_group_id AS id, ag.asset_group_name AS label
    FROM asset_groups ag
    WHERE ag.asset_group_id IN (
        SELECT DISTINCT iag.asset_group_id
        FROM injects i
        INNER JOIN findings f ON f.finding_inject_id = i.inject_id
        INNER JOIN injects_asset_groups iag ON iag.inject_id = i.inject_id
    ) AND (:name IS NULL OR LOWER(ag.asset_group_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')));
    """,
      nativeQuery = true)
  List<Object[]> findAllByNameLinkedToFindings(@Param("name") String name, Pageable pageable);

  @Query(
      value =
          """
    SELECT DISTINCT ag.asset_group_id AS id, ag.asset_group_name AS label
    FROM asset_groups ag
    WHERE ag.asset_group_id IN (
        SELECT DISTINCT iag.asset_group_id
        FROM injects i
        INNER JOIN findings f ON f.finding_inject_id = i.inject_id
        LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
        LEFT JOIN injects_asset_groups iag ON iag.inject_id = i.inject_id
        LEFT JOIN scenarios_exercises se ON se.exercise_id = i.inject_exercise
        WHERE i.inject_id = :sourceId OR i.inject_exercise = :sourceId OR se.scenario_id = :sourceId OR fa.asset_id = :sourceId
    ) AND (:name IS NULL OR LOWER(ag.asset_group_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')));
    """,
      nativeQuery = true)
  List<Object[]> findAllByNameLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("name") String name, Pageable pageable);

  @Query(
      value =
          "SELECT ag.asset_group_id, ag.asset_group_name, ag.asset_group_updated_at, ag.asset_group_created_at "
              + "FROM asset_groups ag "
              + "WHERE ag.asset_group_updated_at > :from ORDER BY ag.asset_group_updated_at LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawAssetGroupIndexing> findForIndexing(@Param("from") Instant from);
}
