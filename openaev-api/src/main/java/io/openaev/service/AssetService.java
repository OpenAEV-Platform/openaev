package io.openaev.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import io.openaev.api.asset.AssetOptionOutput;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetType;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.asset.form.AssetBulkProcessingInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

@RequiredArgsConstructor
@Service
public class AssetService {

  @PersistenceContext private EntityManager entityManager;

  private final AssetRepository assetRepository;
  private final SecurityPlatformRepository securityPlatformRepository;
  private final BulkDeleteExecutor bulkDeleteExecutor;

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

  /**
   * Bulk delete of assets, either from an explicit list of ids or from a search input (select-all
   * with optional exclusions). Security platforms are always excluded from the deletion scope: they
   * are managed from their dedicated area and never surface in the unified inventory.
   *
   * <p>Deliberately NOT transactional as a whole: the scope is resolved in a short read
   * transaction, then assets are deleted in small independent chunks (with deadlock retry) so the
   * request never holds row locks against concurrent agent check-ins for its whole duration.
   *
   * @param input the bulk processing input (ids or search input, plus ids to ignore)
   * @return the ids of the deleted assets
   */
  public List<String> bulkDeleteAssets(@NotNull final AssetBulkProcessingInput input) {
    if ((CollectionUtils.isEmpty(input.getAssetIdsToProcess())
            && input.getSearchPaginationInput() == null)
        || (!CollectionUtils.isEmpty(input.getAssetIdsToProcess())
            && input.getSearchPaginationInput() != null)) {
      throw new BadRequestException(
          "Either asset_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    List<String> assetIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
            () -> {
              Specification<Asset> specification;
              if (input.getSearchPaginationInput() != null) {
                // Same specification chain as the inventory search (filter group + text search),
                // so the deletion scope matches exactly what the user sees in the list.
                specification =
                    FilterUtilsJpa.<Asset>computeFilterGroupJpa(
                            input.getSearchPaginationInput().getFilterGroup())
                        .and(computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
              } else {
                specification = SpecificationUtils.hasIdIn(input.getAssetIdsToProcess());
              }
              if (!CollectionUtils.isEmpty(input.getAssetIdsToIgnore())) {
                List<String> idsToIgnore = input.getAssetIdsToIgnore();
                specification =
                    specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
              }
              // Security platforms are managed from their dedicated area and never surface in the
              // unified inventory: keep them out of the bulk scope even when their ids are
              // explicitly provided.
              specification =
                  specification.and(
                      (root, query, cb) ->
                          cb.notEqual(root.get("type"), AssetType.Values.SECURITY_PLATFORM_TYPE));
              // Project only the ids: bulk scopes can span very large inventories, so loading
              // the full entities just to extract ids would create a needless memory spike and
              // lengthen the scope-resolution transaction.
              return resolveAssetIds(specification);
            });
    return bulkDeleteExecutor.deleteInChunks(
        "assets",
        assetIdsToDelete,
        chunk -> this.assetRepository.deleteAll(this.assetRepository.findAllById(chunk)));
  }

  // Criteria query selecting only the id column, so scope resolution never materialises full
  // Asset entities (goes through the Hibernate session, so the tenant filter still applies).
  private List<String> resolveAssetIds(final Specification<Asset> specification) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<String> query = cb.createQuery(String.class);
    Root<Asset> root = query.from(Asset.class);
    query.select(root.get("id"));
    Predicate predicate = specification.toPredicate(root, query, cb);
    if (predicate != null) {
      query.where(predicate);
    }
    return entityManager.createQuery(query).getResultList();
  }

  public List<SecurityPlatform> securityPlatformsByIds(@NotNull final Set<String> ids) {
    return securityPlatformRepository.findAllByIds(ids);
  }

  public Iterable<Asset> assetFromIds(@NotNull final List<String> assetIds) {
    return this.assetRepository.findAllById(assetIds);
  }

  // -- OPTIONS --

  /**
   * Name-based filter options over the full asset inventory (every category except security
   * platforms). Findings can attach to any asset - not only endpoints - so filter builders (e.g.
   * notification trigger criteria on findings) must propose all of them. Each option carries the
   * asset category so pickers can group the inventory.
   */
  public List<AssetOptionOutput> getOptionsByName(final String searchText, Pageable pageable) {
    // The repository query requires a non-null term (null binds break PostgreSQL type inference);
    // an empty string matches every asset.
    String term = StringUtils.trimToEmpty(searchText);
    return this.assetRepository.findAllOptionsByName(term, pageable).stream()
        .map(
            i ->
                new AssetOptionOutput(
                    (String) i[0],
                    (String) i[1],
                    i[2] != null ? ((AssetCategory) i[2]).name() : null))
        .toList();
  }

  /**
   * Resolve filter option labels for a set of asset ids, whatever the asset category. Security
   * platforms are excluded, consistent with {@link #getOptionsByName(String, Pageable)}: they never
   * surface in the unified inventory options. Null or empty input resolves to an empty list (the
   * ids come straight from a request body that may be absent).
   */
  public List<FilterUtilsJpa.Option> getOptionsByIds(final List<String> ids) {
    if (CollectionUtils.isEmpty(ids)) {
      return List.of();
    }
    // instanceof rather than the type discriminator string: the discriminator field is
    // read-only (insertable = false) and not hydrated on entities created in the current
    // persistence context, while the concrete class is always reliable.
    return fromIterable(this.assetRepository.findAllById(ids)).stream()
        .filter(a -> !(a instanceof SecurityPlatform))
        .map(a -> new FilterUtilsJpa.Option(a.getId(), a.getName()))
        .toList();
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
