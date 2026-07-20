package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetType;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AssetService {

  @PersistenceContext private EntityManager entityManager;

  private final AssetRepository assetRepository;
  private final SecurityPlatformRepository securityPlatformRepository;

  public Asset asset(@NotBlank final String assetId) {
    return this.assetRepository
        .findById(assetId)
        .orElseThrow(() -> new ElementNotFoundException("Asset not found with id: " + assetId));
  }

  public List<Asset> assets(@NotNull final List<String> assetIds) {
    return fromIterable(this.assetRepository.findAllById(assetIds));
  }

  public List<Asset> assets() {
    return fromIterable(this.assetRepository.findAll());
  }

  /**
   * Resolve assets of any type (base Asset, Endpoint, SecurityPlatform) matching the given
   * specification. Used for dynamic asset group resolution, which must span every asset category -
   * not only endpoints - so that e.g. a {@code Category = AI_TARGET} group resolves its AI targets.
   */
  public List<Asset> assets(@NotNull final Specification<Asset> specification) {
    return fromIterable(this.assetRepository.findAll(specification));
  }

  /**
   * Paginated search over EVERY asset type (base Asset, Endpoint, SecurityPlatform, AI targets) for
   * the unified asset inventory. Queries the base {@code assets} table so all categories are
   * returned; filters/sorts must therefore reference base {@link Asset} attributes (endpoint-only
   * fields such as platform / arch live on the subclass and cannot be resolved on the base root).
   */
  public Page<Asset> searchAssets(@NotNull final SearchPaginationInput searchPaginationInput) {
    // Security platforms are a distinct concept with their own inventory, so they are excluded
    // here; the unified asset inventory lists endpoints, AI targets and every other asset category.
    Specification<Asset> notSecurityPlatform =
        (root, query, cb) -> cb.notEqual(root.get("type"), AssetType.Values.SECURITY_PLATFORM_TYPE);
    return buildPaginationJPA(
        (Specification<Asset> specification, Pageable pageable) ->
            this.assetRepository.findAll(notSecurityPlatform.and(specification), pageable),
        searchPaginationInput,
        Asset.class);
  }

  /**
   * Delete any asset by id (endpoint, AI target, or any other category). Used by the unified asset
   * inventory so a single call handles every asset type. Security platforms are managed from their
   * dedicated area and never surface in the inventory, so they are rejected here.
   */
  public void deleteAsset(@NotBlank final String assetId) {
    Asset asset = asset(assetId);
    if (AssetType.Values.SECURITY_PLATFORM_TYPE.equals(asset.getType())) {
      throw new UnsupportedOperationException(
          "Security platforms must be deleted from their dedicated area");
    }
    this.assetRepository.delete(asset);
  }

  public List<SecurityPlatform> securityPlatformsByIds(@NotNull final Set<String> ids) {
    return securityPlatformRepository.findAllByIds(ids);
  }

  public Iterable<Asset> assetFromIds(@NotNull final List<String> assetIds) {
    return this.assetRepository.findAllById(assetIds);
  }

  @Transactional
  public void saveAllAssets(List<Asset> assets) {
    // Improve perfs for save all
    for (int i = 0; i < assets.size(); i++) {
      assetRepository.save(assets.get(i));
      // Flush and clear the session every 50 (batch_size property) inserts
      if (i % 50 == 0) {
        entityManager.flush();
        entityManager.clear();
      }
    }
  }
}
