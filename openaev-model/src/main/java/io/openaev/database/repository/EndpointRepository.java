package io.openaev.database.repository;

import io.openaev.database.model.AssetType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.raw.RawEndpoint;
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

  @Query(
      value =
          "WITH changed_assets AS ("
              + "SELECT a.asset_id FROM assets a WHERE a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' AND a.asset_updated_at > :from "
              + "UNION "
              + "SELECT ia.asset_id FROM injects_assets ia "
              + "JOIN injects i ON ia.inject_id = i.inject_id "
              + "JOIN assets a ON ia.asset_id = a.asset_id AND a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' WHERE i.inject_updated_at > :from "
              + "UNION "
              + "SELECT ia.asset_id FROM injects_assets ia "
              + "JOIN injects i ON ia.inject_id = i.inject_id "
              + "JOIN exercises e ON i.inject_exercise = e.exercise_id "
              + "JOIN assets a ON ia.asset_id = a.asset_id AND a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' WHERE e.exercise_updated_at > :from "
              + "UNION "
              + "SELECT ia.asset_id FROM injects_assets ia "
              + "JOIN injects i ON ia.inject_id = i.inject_id "
              + "JOIN scenarios s ON i.inject_scenario = s.scenario_id "
              + "JOIN assets a ON ia.asset_id = a.asset_id AND a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' WHERE s.scenario_updated_at > :from "
              + "UNION "
              + "SELECT fa.asset_id FROM findings_assets fa "
              + "JOIN findings f ON fa.finding_id = f.finding_id "
              + "JOIN assets a ON fa.asset_id = a.asset_id AND a.asset_type = '"
              + AssetType.Values.ENDPOINT_TYPE
              + "' WHERE f.finding_updated_at > :from"
              + "), "
              + "inj_maxes AS ("
              + "SELECT ia.asset_id, max(i.inject_updated_at) AS max_inj, max(e.exercise_updated_at) AS max_ex, max(s.scenario_updated_at) AS max_sc "
              + "FROM injects_assets ia "
              + "JOIN injects i ON i.inject_id = ia.inject_id "
              + "LEFT JOIN exercises e ON e.exercise_id = i.inject_exercise "
              + "LEFT JOIN scenarios s ON s.scenario_id = i.inject_scenario "
              + "WHERE ia.asset_id IN (SELECT asset_id FROM changed_assets) "
              + "GROUP BY ia.asset_id"
              + "), "
              + "find_maxes AS ("
              + "SELECT fa.asset_id, max(f.finding_updated_at) AS max_find "
              + "FROM findings_assets fa JOIN findings f ON f.finding_id = fa.finding_id "
              + "WHERE fa.asset_id IN (SELECT asset_id FROM changed_assets) "
              + "GROUP BY fa.asset_id"
              + "), "
              + "ranked_assets AS ("
              + "SELECT ca.asset_id, GREATEST(a.asset_updated_at, im.max_inj, im.max_ex, im.max_sc, fm.max_find) AS asset_sort "
              + "FROM changed_assets ca "
              + "JOIN assets a ON a.asset_id = ca.asset_id "
              + "LEFT JOIN inj_maxes im ON im.asset_id = ca.asset_id "
              + "LEFT JOIN find_maxes fm ON fm.asset_id = ca.asset_id "
              + "ORDER BY asset_sort ASC LIMIT :limit"
              + "), "
              + "findings_agg AS ("
              + "SELECT fa.asset_id, array_agg(DISTINCT fa.finding_id) AS asset_findings "
              + "FROM findings_assets fa JOIN ranked_assets ra ON ra.asset_id = fa.asset_id GROUP BY fa.asset_id"
              + "), "
              + "tags_agg AS ("
              + "SELECT at.asset_id, array_agg(DISTINCT at.tag_id) AS asset_tags "
              + "FROM assets_tags at JOIN ranked_assets ra ON ra.asset_id = at.asset_id GROUP BY at.asset_id"
              + "), "
              + "ex_agg AS ("
              + "SELECT ia.asset_id, "
              + "array_agg(DISTINCT i.inject_exercise) FILTER ( WHERE i.inject_exercise IS NOT NULL ) as endpoint_exercises, "
              + "array_agg(DISTINCT i.inject_scenario) FILTER ( WHERE i.inject_scenario IS NOT NULL ) as endpoint_scenarios "
              + "FROM injects_assets ia JOIN ranked_assets ra ON ra.asset_id = ia.asset_id JOIN injects i ON i.inject_id = ia.inject_id "
              + "GROUP BY ia.asset_id"
              + ") "
              + "SELECT a.asset_id, a.asset_type, a.asset_category, a.asset_name, a.asset_external_reference, "
              + "a.asset_ips as endpoint_ips, a.asset_hostname as endpoint_hostname, a.endpoint_platform, a.endpoint_arch, "
              + "a.asset_mac_addresses as endpoint_mac_addresses, a.asset_seen_ip as endpoint_seen_ip, a.asset_created_at, a.endpoint_is_eol, a.asset_description, a.tenant_id, "
              + "ra.asset_sort as endpoint_updated_at, "
              + "fa.asset_findings, ta.asset_tags, xa.endpoint_exercises, xa.endpoint_scenarios "
              + "FROM assets a "
              + "JOIN ranked_assets ra ON ra.asset_id = a.asset_id "
              + "LEFT JOIN findings_agg fa ON fa.asset_id = a.asset_id "
              + "LEFT JOIN tags_agg ta ON ta.asset_id = a.asset_id "
              + "LEFT JOIN ex_agg xa ON xa.asset_id = a.asset_id "
              + "ORDER BY ra.asset_sort ASC;",
      nativeQuery = true)
  List<RawEndpoint> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);

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
