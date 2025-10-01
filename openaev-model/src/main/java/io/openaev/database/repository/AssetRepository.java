package io.openaev.database.repository;

import io.openaev.database.model.Asset;
import io.openaev.database.raw.RawAsset;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository
    extends CrudRepository<Asset, String>, JpaSpecificationExecutor<Asset> {

  @Query("select a from Asset a where a.type IN :types")
  List<Asset> findByType(@Param("types") final List<String> types);

  /**
   * Returns the raw assets having the ids passed in parameter
   *
   * @param ids the ids
   * @return the list of raw assets
   */
  @Query(
      value =
          "SELECT asset_id, asset_name, asset_type, asset_external_reference, endpoint_platform, "
              + "asset_created_at, asset_updated_at "
              + "FROM assets "
              + "WHERE asset_id IN :ids ;",
      nativeQuery = true)
  List<RawAsset> rawByIds(@Param("ids") List<String> ids);

  @Query(
      value =
          "SELECT DISTINCT a.asset_id, a.asset_name, a.asset_type, a.asset_external_reference, a.endpoint_platform, "
              + "a.asset_created_at, a.asset_updated_at "
              + "FROM assets a "
              + "LEFT JOIN injects_assets ia ON a.asset_id = ia.asset_id "
              + "WHERE a.asset_id IN (:assetIds) OR ia.inject_id IN (:injectIds) ;",
      nativeQuery = true)
  List<RawAsset> rawByIdsOrInjectIds(
      @Param("assetIds") Set<String> assetIds, @Param("injectIds") Set<String> injectIds);

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
}
