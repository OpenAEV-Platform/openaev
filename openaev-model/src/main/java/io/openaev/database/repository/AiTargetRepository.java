package io.openaev.database.repository;

import io.openaev.database.model.Endpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Category-scoped view over the {@code assets} table for AI targets ({@code category = AI_TARGET}).
 * AI targets are no longer a distinct entity type - they are {@link Endpoint} rows discriminated by
 * category - so every query here is filtered on {@code asset_category = 'AI_TARGET'} to keep the
 * {@code /api/ai_targets} facade behaving as a dedicated AI target surface.
 *
 * <p>The entity type is {@link Endpoint}, not the base {@code Asset}: AI targets must be visible
 * through the {@code Endpoint}-typed surfaces ({@code /api/endpoints/search}, {@code
 * /api/endpoints/{id}}, the atomic-testing target picker). They stay agentless (empty {@code
 * agents}) - the endpoint discriminator only makes them addressable as targets.
 *
 * <p>Deliberately extends the marker {@link org.springframework.data.repository.Repository} rather
 * than {@code CrudRepository}: inherited generic methods ({@code findAll()}, {@code findById()},
 * ...) are NOT category-scoped and would let call sites silently fetch non-AI assets through this
 * facade. Only the explicitly declared, category-scoped methods (plus the write operations used
 * after a scoped lookup) are exposed. {@link JpaSpecificationExecutor} remains for paginated
 * search, where callers must compose their specification with the AI target category restriction.
 */
@Repository
public interface AiTargetRepository
    extends org.springframework.data.repository.Repository<Endpoint, String>,
        JpaSpecificationExecutor<Endpoint> {

  // -- Write operations (used after a category-scoped lookup / preparation) --

  Endpoint save(Endpoint asset);

  void delete(Endpoint asset);

  // -- Category-scoped queries --

  @Query(
      "SELECT DISTINCT a FROM Endpoint a WHERE a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  List<Endpoint> findAllAiTargets();

  @Query(
      "SELECT DISTINCT a FROM Endpoint a "
          + "WHERE a.category = io.openaev.database.model.AssetCategory.AI_TARGET AND "
          + "(:name IS NULL OR lower(a.name) LIKE lower(concat('%', cast(coalesce(:name, '') as string), '%')))")
  List<Endpoint> findAllByName(String name);

  @Query(
      "SELECT a FROM Endpoint a "
          + "WHERE a.id = :id AND a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  Optional<Endpoint> findAiTargetById(String id);

  /** AI target assets among the given ids (non-AI assets are filtered out by the query). */
  @Query(
      "SELECT a FROM Endpoint a "
          + "WHERE a.id IN :ids AND a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  List<Endpoint> findAiTargetsByIds(List<String> ids);

  /** AI target assets that statically belong to any of the given asset groups. */
  @Query(
      "SELECT DISTINCT a FROM Endpoint a JOIN a.assetGroups g "
          + "WHERE g.id IN :assetGroupIds AND a.category = io.openaev.database.model.AssetCategory.AI_TARGET")
  List<Endpoint> findAllByAssetGroupIds(List<String> assetGroupIds);
}
