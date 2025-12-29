package io.openaev.database.repository;

import io.openaev.database.model.AssetType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.raw.RawEndpoint;
import io.openaev.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EndpointRepository
    extends CrudRepository<Endpoint, String>,
        StatisticRepository,
        JpaSpecificationExecutor<Endpoint> {

  @Query(
      value =
          "select e.* from assets e where e.endpoint_hostname = :hostname and e.endpoint_ips && cast(:ips as text[])",
      nativeQuery = true)
  List<Endpoint> findByHostnameAndAtleastOneIp(
      @NotBlank final @Param("hostname") String hostname,
      @NotNull final @Param("ips") String[] ips);

  @Query(
      value =
          "select e.* from assets e where LOWER(e.endpoint_hostname) = LOWER(:hostname) "
              + "and exists (select 1 from unnest(e.endpoint_mac_addresses) as mac "
              + "where mac = any(select LOWER(REPLACE(REPLACE(m, ':', ''), '-', '')) from unnest(cast(:macAddresses as text[])) as m))",
      nativeQuery = true)
  List<Endpoint> findByHostnameAndAtleastOneMacAddress(
      @Param("hostname") String hostname, @Param("macAddresses") String[] macAddresses);

  @Query(
      value =
          "select e.* from assets e where e.endpoint_mac_addresses && cast(:macAddresses as text[]) order by e.asset_id",
      nativeQuery = true)
  List<Endpoint> findByAtleastOneMacAddress(
      @NotNull final @Param("macAddresses") String[] macAddresses);

  @Query(
      value =
          "select e.* from assets e where e.asset_external_reference = :externalReference order by e.asset_id",
      nativeQuery = true)
  List<Endpoint> findByExternalReference(
      @NotNull final @Param("externalReference") String externalReference);

  @Override
  @Query(
      "select COUNT(DISTINCT a) from Inject i "
          + "join i.assets as a "
          + "join i.exercise as e "
          + "join e.grants as grant "
          + "join grant.group.users as user "
          + "where user.id = :userId and i.createdAt > :creationDate")
  long userCount(@Param("userId") String userId, @Param("creationDate") Instant creationDate);

  @Override
  @Query("select count(distinct e) from Endpoint e where e.createdAt > :creationDate")
  long globalCount(@Param("creationDate") Instant creationDate);

  @Query(
      "SELECT a FROM Inject i"
          + " JOIN i.assets a"
          + " WHERE ("
          + "   :simulationOrScenarioId is NULL AND i.exercise.id is NULL AND i.scenario.id IS NULL"
          + "   OR (i.exercise.id = :simulationOrScenarioId"
          + "   OR i.scenario.id = :simulationOrScenarioId)"
          + " ) AND (:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<Endpoint> findAllBySimulationOrScenarioIdAndName(String simulationOrScenarioId, String name);

  @Query(
      value =
          "SELECT DISTINCT e.* "
              + "FROM assets e "
              + "INNER JOIN injects_assets ia ON e.asset_id = ia.asset_id",
      nativeQuery = true)
  List<Endpoint> findAllEndpointsForAtomicTestingsSimulationsAndScenarios();

  @Query(
      value =
          """
              SELECT DISTINCT a.asset_id AS id, a.asset_name AS label
              FROM assets a
              WHERE a.asset_id IN (
                  SELECT DISTINCT fa.asset_id
                  FROM findings f
                  LEFT JOIN findings_assets fa ON fa.finding_id = f.finding_id
              ) AND (:name IS NULL OR LOWER(a.asset_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')));
              """,
      nativeQuery = true)
  List<Object[]> findAllByNameLinkedToFindings(@Param("name") String name, Pageable pageable);

  @Query(
      value =
          """
              SELECT DISTINCT a.asset_id AS id, a.asset_name AS label
              FROM assets a
              WHERE a.asset_id IN (
                  SELECT DISTINCT fa2.asset_id
                  FROM findings_assets fa1
                  INNER JOIN findings f ON f.finding_id = fa1.finding_id
                  INNER JOIN findings_assets fa2 ON f.finding_id = fa2.finding_id
                  INNER JOIN injects i ON f.finding_inject_id = i.inject_id
                  LEFT JOIN scenarios_exercises se ON se.exercise_id = i.inject_exercise
                  WHERE (
                      fa1.asset_id = :sourceId
                      OR i.inject_id = :sourceId
                      OR i.inject_exercise = :sourceId
                      OR se.scenario_id = :sourceId
                  )
                  AND fa2.asset_id != :sourceId
              )
              AND (:name IS NULL OR LOWER(a.asset_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')));
              """,
      nativeQuery = true)
  List<Object[]> findAllByNameLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("name") String name, Pageable pageable);

  @Query(
      value =
          "WITH endpoint_data AS ("
              + "SELECT a.asset_id, a.asset_type, a.asset_name, a.asset_external_reference, "
              + "a.endpoint_ips, a.endpoint_hostname, a.endpoint_platform, a.endpoint_arch, "
              + "a.endpoint_mac_addresses, a.endpoint_seen_ip, a.asset_created_at, a.endpoint_is_eol, a.asset_description, "
              + "GREATEST(a.asset_updated_at, max(i.inject_updated_at), max(e.exercise_updated_at), max(s.scenario_updated_at), max(f.finding_updated_at)) as endpoint_updated_at, "
              + "array_agg(DISTINCT fa.finding_id) FILTER ( WHERE fa.finding_id IS NOT NULL ) as asset_findings, "
              + "array_agg(DISTINCT at.tag_id) FILTER ( WHERE at.tag_id IS NOT NULL ) as asset_tags, "
              + "array_agg(DISTINCT i.inject_exercise) FILTER ( WHERE i.inject_exercise IS NOT NULL ) as endpoint_exercises, "
              + "array_agg(DISTINCT i.inject_scenario) FILTER ( WHERE i.inject_scenario IS NOT NULL ) as endpoint_scenarios "
              + "FROM assets a "
              + "LEFT JOIN findings_assets fa ON a.asset_id = fa.asset_id "
              + "LEFT JOIN findings f ON fa.finding_id = f.finding_id "
              + "LEFT JOIN assets_tags at ON a.asset_id = at.asset_id "
              + "LEFT JOIN injects_assets ia ON a.asset_id = ia.asset_id "
              + "LEFT JOIN injects i ON ia.inject_id = i.inject_id "
              + "LEFT JOIN exercises e ON i.inject_exercise = e.exercise_id "
              + "LEFT JOIN scenarios s ON i.inject_scenario = s.scenario_id "
              + "WHERE a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' "
              + "GROUP BY a.asset_id"
              + ") "
              + "SELECT * FROM endpoint_data ed "
              + "WHERE ed.endpoint_updated_at > :from "
              + "ORDER BY ed.endpoint_updated_at ASC LIMIT "
              + Constants.INDEXING_RECORD_SET_SIZE
              + ";",
      nativeQuery = true)
  List<RawEndpoint> findForIndexing(@Param("from") Instant from);

  // For testing purposes only

  @Modifying
  @Query(
      value = "UPDATE assets SET asset_created_at = :creationDate where asset_id = :id",
      nativeQuery = true)
  void setCreationDate(@Param("creationDate") Instant creationDate, @Param("id") String assetId);

  @Modifying
  @Query(
      value = "UPDATE assets SET asset_updated_at = :updateDate where asset_id = :id",
      nativeQuery = true)
  void setUpdateDate(@Param("updateDate") Instant updateDate, @Param("id") String assetId);

  // Replace Hibernate query by native query for perfs
  // Native query does the same as Hibernate query here because all "cascade" and other relations
  // are properly set in the database
  @Modifying
  @Query(value = "DELETE FROM assets WHERE asset_id = :assetId", nativeQuery = true)
  void deleteById(@Param("assetId") @NotBlank String assetId);

  List<Endpoint> findDistinctByInjectsScenarioId(String scenarioId);

  List<Endpoint> findDistinctByInjectsScenarioIdAndIdIn(String scenarioId, List<String> ids);

  List<Endpoint> findDistinctByInjectsExerciseId(String exerciseId);

  List<Endpoint> findDistinctByInjectsExerciseIdAndIdIn(String exerciseId, List<String> ids);
}
