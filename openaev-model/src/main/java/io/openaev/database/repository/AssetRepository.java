package io.openaev.database.repository;

import io.openaev.database.model.Asset;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
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
}
