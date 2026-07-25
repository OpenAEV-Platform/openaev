package io.openaev.database.repository;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetType;
import io.openaev.database.raw.RawIndexedAsset;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository
    extends CrudRepository<Asset, String>, JpaSpecificationExecutor<Asset> {

  /**
   * Feeds the {@code asset} search index with the whole asset inventory: every asset type except
   * security platforms, which have their own index and never surface in the inventory (mirrors
   * {@code AssetService.searchAssets}). Host-only columns come from the Endpoint subclass rows of
   * the single {@code assets} table and are simply null elsewhere.
   *
   * <p>An asset is re-indexed when it changes itself or when anything denormalized into its
   * document changes (injects, their simulation / scenario, findings), and the returned {@code
   * asset_indexed_at} carries that same maximum so the incremental indexing cursor cannot skip a
   * document.
   */
  @Query(
      value =
          "WITH changed_assets AS ("
              + "SELECT a.asset_id FROM assets a WHERE a.asset_type <> '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
              + "' AND a.asset_updated_at > :from "
              + "UNION "
              + "SELECT ia.asset_id FROM injects_assets ia "
              + "JOIN injects i ON ia.inject_id = i.inject_id "
              + "JOIN assets a ON ia.asset_id = a.asset_id AND a.asset_type <> '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
              + "' WHERE i.inject_updated_at > :from "
              + "UNION "
              + "SELECT ia.asset_id FROM injects_assets ia "
              + "JOIN injects i ON ia.inject_id = i.inject_id "
              + "JOIN exercises e ON i.inject_exercise = e.exercise_id "
              + "JOIN assets a ON ia.asset_id = a.asset_id AND a.asset_type <> '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
              + "' WHERE e.exercise_updated_at > :from "
              + "UNION "
              + "SELECT ia.asset_id FROM injects_assets ia "
              + "JOIN injects i ON ia.inject_id = i.inject_id "
              + "JOIN scenarios s ON i.inject_scenario = s.scenario_id "
              + "JOIN assets a ON ia.asset_id = a.asset_id AND a.asset_type <> '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
              + "' WHERE s.scenario_updated_at > :from "
              + "UNION "
              + "SELECT fa.asset_id FROM findings_assets fa "
              + "JOIN findings f ON fa.finding_id = f.finding_id "
              + "JOIN assets a ON fa.asset_id = a.asset_id AND a.asset_type <> '"
              + AssetType.Values.SECURITY_PLATFORM_TYPE
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
              + "array_agg(DISTINCT i.inject_exercise) FILTER ( WHERE i.inject_exercise IS NOT NULL ) as asset_exercises, "
              + "array_agg(DISTINCT i.inject_scenario) FILTER ( WHERE i.inject_scenario IS NOT NULL ) as asset_scenarios "
              + "FROM injects_assets ia JOIN ranked_assets ra ON ra.asset_id = ia.asset_id JOIN injects i ON i.inject_id = ia.inject_id "
              + "GROUP BY ia.asset_id"
              + ") "
              + "SELECT a.asset_id, a.asset_type, a.asset_category, a.asset_name, a.asset_external_reference, "
              + "a.asset_ips, a.asset_hostname, a.endpoint_platform, a.endpoint_arch, "
              + "a.asset_mac_addresses, a.asset_seen_ip, a.asset_created_at, a.asset_updated_at, a.endpoint_is_eol, a.asset_description, a.tenant_id, "
              + "ra.asset_sort as asset_indexed_at, "
              + "fa.asset_findings, ta.asset_tags, xa.asset_exercises, xa.asset_scenarios "
              + "FROM assets a "
              + "JOIN ranked_assets ra ON ra.asset_id = a.asset_id "
              + "LEFT JOIN findings_agg fa ON fa.asset_id = a.asset_id "
              + "LEFT JOIN tags_agg ta ON ta.asset_id = a.asset_id "
              + "LEFT JOIN ex_agg xa ON xa.asset_id = a.asset_id "
              + "ORDER BY ra.asset_sort ASC;",
      nativeQuery = true)
  List<RawIndexedAsset> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);

  @Query(
      value =
          "SELECT DISTINCT i.inject_exercise, a.asset_id, a.asset_name "
              + "FROM assets a "
              + "INNER JOIN injects_assets ia ON a.asset_id = ia.asset_id "
              + "INNER JOIN injects i ON ia.inject_id = i.inject_id "
              + "WHERE i.inject_exercise in :exerciseIds",
      nativeQuery = true)
  List<Object[]> assetsByExerciseIds(Set<String> exerciseIds);

  @Query(
      value =
          "SELECT DISTINCT ia.inject_id, a.asset_id, a.asset_name "
              + "FROM assets a "
              + "INNER JOIN injects_assets ia ON a.asset_id = ia.asset_id "
              + "WHERE ia.inject_id in :injectIds",
      nativeQuery = true)
  List<Object[]> assetsByInjectIds(Set<String> injectIds);

  List<Asset> findByTenantId(String tenantId);

  /**
   * Business criticality and display name for a set of asset ids, as {@code [assetId,
   * AssetCriticality, name]} rows. Used by the attack-path chokepoint score (findings weighted by
   * criticality) and to label an endpoint node with its asset name. JPQL (not native) so the tenant
   * filter still applies.
   */
  @Query("SELECT a.id, a.criticality, a.name FROM Asset a WHERE a.id IN :ids")
  List<Object[]> findCriticalityByIds(Set<String> ids);

  /**
   * Name-based option search over EVERY asset type except security platforms (endpoints - agent
   * based or agentless -, AI targets, cloud / web / network / generic assets). Findings can attach
   * to any asset, so filter options (e.g. notification trigger criteria) must propose the full
   * inventory, not only endpoints. The category is returned so pickers can group options by asset
   * category. JPQL (not native) so the tenant filter still applies. The {@code name} parameter must
   * be non-null (empty string matches everything): a null bind parameter inside {@code
   * lower(concat(...))} is typed as bytea by PostgreSQL and fails.
   */
  @Query(
      "SELECT a.id, a.name, a.category FROM Asset a "
          + "WHERE a.type <> 'SecurityPlatform' "
          + "AND lower(a.name) LIKE lower(concat('%', :name, '%')) "
          // id tie-breaker: names are not unique, and a fixed page size over a
          // non-deterministic order would return unstable option subsets
          + "ORDER BY a.name, a.id")
  List<Object[]> findAllOptionsByName(@Param("name") String name, Pageable pageable);
}
