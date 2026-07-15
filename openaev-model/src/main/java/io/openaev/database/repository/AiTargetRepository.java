package io.openaev.database.repository;

import io.openaev.database.model.Asset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Category-scoped view over the {@code assets} table for AI targets ({@code category = AI_TARGET}).
 * AI targets are no longer a distinct entity type - they are {@link Asset} rows discriminated by
 * category - so every query here is filtered on {@code asset_category = 'AI_TARGET'} to keep the
 * {@code /api/ai_targets} facade behaving as a dedicated AI target surface.
 */
@Repository
public interface AiTargetRepository
    extends CrudRepository<Asset, String>, JpaSpecificationExecutor<Asset> {

  @Query(
      "SELECT DISTINCT a FROM Asset a WHERE a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  List<Asset> findAllAiTargets();

  @Query(
      "SELECT DISTINCT a FROM Asset a "
          + "WHERE a.category = io.openaev.database.model.AssetCategory.AI_TARGET AND "
          + "(:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<Asset> findAllByName(String name);

  @Query(
      "SELECT a FROM Asset a "
          + "WHERE a.id = :id AND a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  Optional<Asset> findAiTargetById(String id);

  /** AI target assets that statically belong to any of the given asset groups. */
  @Query(
      "SELECT DISTINCT a FROM Asset a JOIN a.assetGroups g "
          + "WHERE g.id IN :assetGroupIds AND a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  List<Asset> findAllByAssetGroupIds(List<String> assetGroupIds);
}
