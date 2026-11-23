package io.openaev.service.catalog.payload;

import static io.openaev.database.specification.CatalogItemSpecification.latestOffered;
import static io.openaev.database.specification.CatalogItemSpecification.payloadNotInstalled;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.service.catalog.CatalogFacet;
import io.openaev.service.catalog.CatalogFacetCounter;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.CatalogSettingsService;
import io.openaev.utils.catalog.SemanticVersion;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue payload versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogPayloadOfferService implements CatalogOffer<InstallablePayloadVersion> {

  private final CatalogPayloadService catalogPayloadService;
  private final CatalogSettingsService catalogSettingsService;
  private final CatalogListService catalogListService;
  private final CatalogFacetCounter catalogFacetCounter;

  // -- LIST --

  /** A released version the catalogue still offers. */
  @Transactional(readOnly = true)
  public InstallablePayloadVersion installable(TxCtx ctx, String externalId, String version) {
    return InstallablePayloadVersion.of(
        catalogListService.offered(CatalogPayload.class, externalId, version));
  }

  /** The latest offered version of every catalogue payload the tenant never installed. */
  @Override
  @Transactional(readOnly = true)
  public List<InstallablePayloadVersion> neverInstalled(TxCtx ctx, String tenantId) {
    return neverInstalledVersions(tenantId);
  }

  /**
   * What installs itself after a release: every newer offered version of an installed payload, in
   * order, and the payloads never installed when the tenant asks for it.
   */
  @Override
  @Transactional(readOnly = true)
  public List<InstallablePayloadVersion> toAutoInstall(TxCtx ctx, String tenantId) {
    List<InstallablePayloadVersion> versions = new ArrayList<>(newerVersions(tenantId));
    if (catalogSettingsService.isAutoInstall(ctx, tenantId, ContentType.PAYLOAD)) {
      versions.addAll(neverInstalledVersions(tenantId));
    }
    return versions;
  }

  // -- SEARCH --

  /** A page of the offered payloads the tenant does not show. */
  @Transactional(readOnly = true)
  public Page<CatalogPayload> searchNotInstalled(
      String tenantId, SearchPaginationInput input, List<CatalogFacet> facets) {
    return catalogListService.search(CatalogPayload.class, notInstalled(tenantId), input, facets);
  }

  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> notInstalledFacetCounts(
      String tenantId, SearchPaginationInput input, List<CatalogFacet> facets) {
    return catalogFacetCounter.count(CatalogPayload.class, notInstalled(tenantId), input, facets);
  }

  // -- STEPS --

  private static Specification<CatalogItem> notInstalled(String tenantId) {
    return latestOffered(tenantId, CatalogPayload.class).and(payloadNotInstalled(tenantId));
  }

  private List<InstallablePayloadVersion> neverInstalledVersions(String tenantId) {
    return catalogListService
        .latestOfferedExcept(CatalogPayload.class, catalogPayloadService.knownKeys(tenantId))
        .stream()
        .map(InstallablePayloadVersion::of)
        .toList();
  }

  // An uninstalled payload has no latest version: none of its versions installs itself.
  private List<InstallablePayloadVersion> newerVersions(String tenantId) {
    Map<String, String> installed = catalogPayloadService.installedVersions(tenantId);
    return catalogListService.allOffered(CatalogPayload.class).stream()
        .filter(
            item -> {
              String latest = installed.get(CatalogOffer.key(item));
              return latest != null && SemanticVersion.ORDER.compare(item.getVersion(), latest) > 0;
            })
        .map(InstallablePayloadVersion::of)
        .sorted(Comparator.comparing(InstallablePayloadVersion::version, SemanticVersion.ORDER))
        .toList();
  }
}
