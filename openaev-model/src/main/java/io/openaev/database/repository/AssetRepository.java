package io.openaev.database.repository;

import io.openaev.database.model.Asset;
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
   * inventory, not only endpoints. JPQL (not native) so the tenant filter still applies. The {@code
   * name} parameter must be non-null (empty string matches everything): a null bind parameter
   * inside {@code lower(concat(...))} is typed as bytea by PostgreSQL and fails.
   */
  @Query(
      "SELECT a.id, a.name FROM Asset a "
          + "WHERE a.type <> 'SecurityPlatform' "
          + "AND lower(a.name) LIKE lower(concat('%', :name, '%')) "
          + "ORDER BY a.name")
  List<Object[]> findAllOptionsByName(@Param("name") String name, Pageable pageable);
}
