package io.openaev.service.catalog.payload;

import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.CatalogSettingsService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue payload versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogPayloadOfferService implements CatalogOffer<InstallablePayloadVersion> {

  private final CatalogPayloadService catalogPayloadService;
  private final CatalogSettingsService catalogSettingsService;
  private final CatalogListService catalogListService;

  // -- LIST --

  /** A released version the catalogue still offers. */
  @Transactional(readOnly = true)
  public InstallablePayloadVersion installable(TxCtx ctx, String externalId, String version) {
    return InstallablePayloadVersion.of(
        catalogListService.offered(CatalogPayload.class, externalId, version));
  }

  /** The latest offered version of every catalogue payload the tenant does not show. */
  @Transactional(readOnly = true)
  public List<CatalogPayload> notInstalled(String tenantId) {
    return catalogListService.latestOfferedExcept(
        CatalogPayload.class, catalogPayloadService.installedVersions(tenantId).keySet());
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

  // -- STEPS --

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
