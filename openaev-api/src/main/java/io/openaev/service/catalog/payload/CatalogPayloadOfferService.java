package io.openaev.service.catalog.payload;

import static io.openaev.database.specification.CatalogItemSpecification.latestOffered;
import static io.openaev.database.specification.CatalogItemSpecification.payloadNotInstalled;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.raw.RawInstalledPayloadVersion;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.service.catalog.CatalogFacet;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.CatalogSettingsService;
import io.openaev.utils.catalog.SemanticVersion;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue payload versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogPayloadOfferService implements CatalogOffer<InstallablePayloadVersion> {

  private static final List<CatalogFacet> FACETS =
      List.of(CatalogFacet.of(CatalogFacet.DOMAIN_KEY), CatalogFacet.PLATFORMS);

  private final CatalogItemRepository catalogItemRepository;
  private final PayloadIdentityRepository payloadIdentityRepository;
  private final CatalogSettingsService catalogSettingsService;
  private final CatalogListService catalogListService;

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
    return neverInstalled(tenantId, catalogItemRepository.findAllPayloads());
  }

  /**
   * What installs itself after a release: every newer offered version of an installed payload, in
   * order, and the payloads never installed when the tenant asks for it.
   */
  @Override
  @Transactional(readOnly = true)
  public List<InstallablePayloadVersion> toAutoInstall(TxCtx ctx, String tenantId) {
    List<CatalogPayload> items = catalogItemRepository.findAllPayloads();
    List<InstallablePayloadVersion> versions = new ArrayList<>(newerVersions(tenantId, items));
    if (catalogSettingsService.isAutoInstall(ctx, tenantId, ContentType.PAYLOAD)) {
      versions.addAll(neverInstalled(tenantId, items));
    }
    return versions;
  }

  // -- SEARCH --

  /** A page of the offered payloads the tenant does not show. */
  @Transactional(readOnly = true)
  public Page<CatalogPayload> searchNotInstalled(String tenantId, SearchPaginationInput input) {
    return catalogListService.search(CatalogPayload.class, notInstalled(tenantId), input);
  }

  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> notInstalledFacetCounts(
      String tenantId, SearchPaginationInput input) {
    return catalogListService.facetCounts(
        CatalogPayload.class, notInstalled(tenantId), input, FACETS);
  }

  // -- STEPS --

  private static Specification<CatalogItem> notInstalled(String tenantId) {
    return latestOffered(tenantId, CatalogPayload.class).and(payloadNotInstalled(tenantId));
  }

  private List<InstallablePayloadVersion> neverInstalled(
      String tenantId, List<CatalogPayload> items) {
    Set<String> known =
        payloadIdentityRepository.findAllByTenantIdAndRepositoryIsNotNull(tenantId).stream()
            .map(CatalogOffer::key)
            .collect(Collectors.toSet());
    return CatalogListService.latestVersions(items).stream()
        .map(InstallablePayloadVersion::of)
        .filter(
            released ->
                !known.contains(CatalogOffer.key(released.repository(), released.externalId())))
        .toList();
  }

  // An uninstalled payload has no latest version: none of its versions installs itself.
  private List<InstallablePayloadVersion> newerVersions(
      String tenantId, List<CatalogPayload> items) {
    Map<String, String> installed = installedVersions(tenantId);
    return items.stream()
        .filter(item -> item.getStatus().isOffered())
        .map(InstallablePayloadVersion::of)
        .filter(
            released -> {
              String latest =
                  installed.get(CatalogOffer.key(released.repository(), released.externalId()));
              return latest != null
                  && SemanticVersion.ORDER.compare(released.version(), latest) > 0;
            })
        .sorted(Comparator.comparing(InstallablePayloadVersion::version, SemanticVersion.ORDER))
        .toList();
  }

  // The version the tenant shows of each installed catalogue payload, by its key.
  private Map<String, String> installedVersions(String tenantId) {
    return payloadIdentityRepository.findInstalledVersions(tenantId).stream()
        .collect(
            Collectors.toMap(
                v -> CatalogOffer.key(v.getRepository(), v.getExternalId()),
                RawInstalledPayloadVersion::getVersion));
  }
}
