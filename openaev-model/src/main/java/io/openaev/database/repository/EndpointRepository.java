package io.openaev.database.repository;

import io.openaev.database.model.AssetType;
import io.openaev.database.model.Endpoint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EndpointRepository
    extends CrudRepository<Endpoint, String>, JpaSpecificationExecutor<Endpoint> {

  // Asset.activityStatus is a @Formula property, so Hibernate expects a result-set column literally
  // named "activityStatus" when hydrating an Endpoint from a native query. A bare "select e.*" does
  // not produce it, which breaks every native Endpoint fetch (e.g. agent registration). Every
  // native
  // query returning Endpoint entities must therefore append this expression (mirrors the formula in
  // Asset, with asset_id qualified as e.asset_id so it stays unambiguous when other tables are
  // joined). Aliased in double quotes so the label case matches the property name exactly.
  String ACTIVITY_STATUS_SELECT =
      ", (CASE"
          + " WHEN NOT EXISTS (SELECT 1 FROM agents ag WHERE ag.agent_asset = e.asset_id)"
          + " THEN 'AGENTLESS'"
          + " WHEN EXISTS (SELECT 1 FROM agents ag WHERE ag.agent_asset = e.asset_id"
          + " AND ag.agent_last_seen > now() - interval '1 hour') THEN 'ACTIVE'"
          + " ELSE 'INACTIVE' END) AS \"activityStatus\"";

  // The asset_hostname / asset_ips / asset_mac_addresses columns now live on the Asset base and
  // can be populated for non-endpoint assets (web, cloud, network categories), so every native
  // query hydrating Endpoint entities must filter on the discriminator explicitly.
  @Query(
      value =
          "select e.*"
              + ACTIVITY_STATUS_SELECT
              + " from assets e where e.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' and e.asset_hostname = :hostname and e.asset_ips && cast(:ips as text[]) and e.tenant_id = :tenantId",
      nativeQuery = true)
  List<Endpoint> findByHostnameAndAtleastOneIp(
      @NotBlank final @Param("hostname") String hostname,
      @NotNull final @Param("ips") String[] ips,
      @NotNull final @Param("tenantId") String tenantId);

  @Query(
      value =
          "select e.*"
              + ACTIVITY_STATUS_SELECT
              + " from assets e where e.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' and LOWER(e.asset_hostname) = LOWER(:hostname) and e.tenant_id = :tenantId "
              + "and exists (select 1 from unnest(e.asset_mac_addresses) as mac "
              + "where mac = any(select LOWER(REPLACE(REPLACE(m, ':', ''), '-', '')) from unnest(cast(:macAddresses as text[])) as m))",
      nativeQuery = true)
  List<Endpoint> findByHostnameAndAtleastOneMacAddress(
      @Param("hostname") String hostname,
      @Param("macAddresses") String[] macAddresses,
      @NotNull @Param("tenantId") String tenantId);

  @Query(
      value =
          "select e.*"
              + ACTIVITY_STATUS_SELECT
              + " from assets e where e.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' and e.asset_mac_addresses && cast(:macAddresses as text[]) and e.tenant_id = :tenantId order by e.asset_id",
      nativeQuery = true)
  List<Endpoint> findByAtleastOneMacAddress(
      @NotNull final @Param("macAddresses") String[] macAddresses,
      @NotNull @Param("tenantId") String tenantId);

  @Query(
      value =
          "select e.*"
              + ACTIVITY_STATUS_SELECT
              + " from assets e where e.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' and e.asset_external_reference = :externalReference and e.tenant_id = :tenantId order by e.asset_id",
      nativeQuery = true)
  List<Endpoint> findByExternalReference(
      @NotNull final @Param("externalReference") String externalReference,
      @NotNull @Param("tenantId") String tenantId);

  @Query(
      "SELECT a FROM Inject i"
          + " JOIN i.assets a"
          + " WHERE ("
          + "   :simulationOrScenarioId is NULL AND i.exercise.id is NULL AND i.scenario.id IS NULL"
          + "   OR (i.exercise.id = :simulationOrScenarioId"
          + "   OR i.scenario.id = :simulationOrScenarioId)"
          + " ) AND (:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))"
          // injects_assets may now reference non-endpoint assets (e.g. AI targets)
          + " AND TYPE(a) = Endpoint"
          + " AND i.tenant.id = :#{#tenantContext.currentTenant}")
  List<Endpoint> findAllBySimulationOrScenarioIdAndName(String simulationOrScenarioId, String name);

  @Query(
      value =
          "SELECT DISTINCT e.*"
              + ACTIVITY_STATUS_SELECT
              + " FROM assets e "
              + "INNER JOIN injects_assets ia ON e.asset_id = ia.asset_id "
              + "WHERE e.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' AND e.tenant_id = :#{#tenantContext.currentTenant}",
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
              ) AND (:name IS NULL OR LOWER(a.asset_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
              AND a.tenant_id = :#{#tenantContext.currentTenant};
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
              AND (:name IS NULL OR LOWER(a.asset_name) LIKE LOWER(CONCAT('%', COALESCE(:name, ''), '%')))
              AND a.tenant_id = :#{#tenantContext.currentTenant};
              """,
      nativeQuery = true)
  List<Object[]> findAllByNameLinkedToFindingsWithContext(
      @Param("sourceId") String sourceId, @Param("name") String name, Pageable pageable);

  // For testing purposes only
  @Modifying
  @Query(
      value = "UPDATE assets SET asset_created_at = :creationDate where asset_id = :id",
      nativeQuery = true)
  void setCreationDate(@Param("creationDate") Instant creationDate, @Param("id") String assetId);

  // For testing purposes only
  @Modifying
  @Query(
      value = "UPDATE assets SET asset_updated_at = :updateDate where asset_id = :id",
      nativeQuery = true)
  void setUpdateDate(@Param("updateDate") Instant updateDate, @Param("id") String assetId);

  // Replace Hibernate query by native query for perfs
  // Native query does the same as Hibernate query here because all "cascade" and other relations
  // are properly set in the database
  @Modifying
  @Query(
      value =
          "DELETE FROM assets WHERE asset_id = :assetId AND tenant_id = :#{#tenantContext.currentTenant}",
      nativeQuery = true)
  void deleteById(@Param("assetId") @NotBlank String assetId);

  List<Endpoint> findDistinctByInjectsScenarioId(String scenarioId);

  List<Endpoint> findDistinctByInjectsScenarioIdAndIdIn(String scenarioId, List<String> ids);

  List<Endpoint> findDistinctByInjectsExerciseId(String exerciseId);

  List<Endpoint> findDistinctByInjectsExerciseIdAndIdIn(String exerciseId, List<String> ids);

  Optional<Endpoint> findByIdAndTenantId(String id, String tenantId);
}
