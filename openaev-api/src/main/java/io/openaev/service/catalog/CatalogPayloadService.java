package io.openaev.service.catalog;

import static io.openaev.database.model.TenantSettingKeys.CATALOG_PAYLOAD_AUTO_INSTALL;

import io.openaev.api.catalog.CatalogManifest;
import io.openaev.api.catalog.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadVariantService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.payload.PayloadVersionChangeService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.payload.PayloadVersionService.Promotion;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.service.threat_arsenal.ThreatArsenalImportService;
import io.openaev.utils.SemanticVersion;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue payloads in a tenant (F3, F26): installed by hand or automatically, as managed versions
 * that only exist once their content matches the release.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CatalogPayloadService {

  private final CatalogService catalogService;
  private final PayloadVersionService payloadVersionService;
  private final PayloadVersionChangeService payloadVersionChangeService;
  private final ThreatArsenalImportService threatArsenalImportService;
  private final InjectorContractService injectorContractService;
  private final ContentHistoryService contentHistoryService;
  private final TenantSettingsService tenantSettingsService;
  private final PayloadVariantService payloadVariantService;

  // -- LIST --

  @Transactional(readOnly = true)
  public boolean isAutoInstall(String tenantId) {
    return autoInstall(tenantId);
  }

  private boolean autoInstall(String tenantId) {
    return Boolean.parseBoolean(
        tenantSettingsService.resolveSettingValue(tenantId, CATALOG_PAYLOAD_AUTO_INSTALL));
  }

  // -- CREATE --

  public Payload install(TxCtx ctx, String tenantId, String externalId, String version)
      throws Exception {
    return install(ctx, tenantId, null, externalId, version);
  }

  /**
   * Installs one released version into its managed identity. A local copy with this content that
   * claims this ID becomes the version when it is the only one, alone in its identity; otherwise
   * the package is imported, kept only if its content has the release hash, and the injects of the
   * copies move to it (RFC 0005 4.2, 4.5).
   *
   * @param repository the release repository; null when only one repository releases this ID
   */
  public Payload install(
      TxCtx ctx, String tenantId, String repository, String externalId, String version)
      throws Exception {
    return installVersion(ctx, tenantId, repository, externalId, version, false);
  }

  private Payload installVersion(
      TxCtx ctx,
      String tenantId,
      String repository,
      String externalId,
      String version,
      boolean automatic)
      throws Exception {
    CatalogItem item =
        catalogService
            .find(ContentType.PAYLOAD, repository, externalId, version)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue payload not found"));
    if ("REVOKED".equals(item.getStatus())) {
      throw new BadRequestException("This version is revoked");
    }
    PayloadIdentity identity =
        payloadVersionService.findOrCreateManagedIdentity(
            tenantId, item.getRepository(), externalId);
    Optional<Payload> installed =
        payloadVersionService.findVersions(identity).stream()
            .filter(p -> version.equals(p.getVersion()))
            .findFirst();
    if (installed.isPresent()) {
      if (!identity.isUninstalled()) {
        throw new BadRequestException("This version is already installed");
      }
      payloadVersionService.setUninstalled(identity, false);
      contentHistoryService.payloadInstalled(installed.get(), automatic);
      return installed.get();
    }
    String hash = contentHash(item);
    Promotion promotion = payloadVersionService.findPromotion(externalId, hash, tenantId);
    Payload payload;
    if (promotion.promotable().isPresent()) {
      payload =
          payloadVersionService.moveToIdentity(promotion.promotable().get(), identity, version);
    } else {
      PayloadIdentity locked = payloadVersionService.lockForVersion(identity, version);
      payload =
          threatArsenalImportService.importCatalogAction(
              ctx, catalogService.readVerifiedPackage(item), tenantId, locked, version);
      if (!hash.equalsIgnoreCase(payload.getContentHash())) {
        throw new BadRequestException(
            "Payload " + payload.getName() + " does not match the release");
      }
      payloadVersionService.refreshLatest(locked);
      Payload release = payload;
      promotion
          .copiesToRepoint()
          .forEach(copy -> payloadVersionChangeService.repointAllUsages(copy, release));
    }
    if (identity.isUninstalled()) {
      payloadVersionService.setUninstalled(identity, false);
    }
    contentHistoryService.payloadInstalled(payload, automatic);
    payloadVariantService.markConflicts(payload);
    return payload;
  }

  /** Latest version of every catalogue payload not installed yet, uninstalled ones aside (F5). */
  public List<String> installAll(TxCtx ctx, String tenantId) throws Exception {
    Set<String> installed = payloadVersionService.findInstalledManagedVersions().keySet();
    Set<String> uninstalled = payloadVersionService.findUninstalledExternalIds();
    List<String> done = new ArrayList<>();
    for (CatalogItem item : latestPerExternalId()) {
      if (!installed.contains(item.getExternalId())
          && !uninstalled.contains(item.getExternalId())
          && !"REVOKED".equals(item.getStatus())) {
        install(ctx, tenantId, item.getRepository(), item.getExternalId(), item.getVersion());
        done.add(item.getExternalId());
      }
    }
    return done;
  }

  // -- SYNC --

  /**
   * After a release: new versions of installed payloads install themselves, identical local copies
   * are linked, and with the tenant setting on, new payloads install too (F3). Uninstalled ones
   * stay out (F26).
   */
  public void afterSync(TxCtx ctx, String tenantId, CatalogManifest manifest) throws Exception {
    boolean autoInstall = autoInstall(tenantId);
    Set<String> uninstalled = payloadVersionService.findUninstalledExternalIds();
    Map<String, Set<String>> installed = payloadVersionService.findInstalledManagedVersions();
    for (Released released : releasedPayloads(manifest)) {
      String externalId = released.externalId();
      if (uninstalled.contains(externalId)
          || installed.getOrDefault(externalId, Set.of()).contains(released.version())) {
        continue;
      }
      Optional<CatalogItem> item =
          catalogService.find(
              ContentType.PAYLOAD, manifest.repository(), externalId, released.version());
      if (item.isEmpty() || "REVOKED".equals(item.get().getStatus())) {
        continue;
      }
      boolean hasCopy =
          payloadVersionService
              .findPromotion(externalId, contentHash(item.get()), tenantId)
              .found();
      // Only newer than what is installed: an older release stays to install by hand.
      boolean newVersionOfInstalled =
          installed.containsKey(externalId)
              && installed.get(externalId).stream()
                  .allMatch(v -> SemanticVersion.ORDER.compare(released.version(), v) > 0);
      boolean newPayload =
          autoInstall && !installed.containsKey(externalId) && isLatest(released, manifest);
      if (hasCopy || newVersionOfInstalled || newPayload) {
        installVersion(ctx, tenantId, manifest.repository(), externalId, released.version(), true);
        installed.computeIfAbsent(externalId, k -> new HashSet<>()).add(released.version());
      }
    }
  }

  // -- UPDATE --

  public void setAutoInstall(String tenantId, boolean enabled) {
    tenantSettingsService.updateCatalogPayloadAutoInstall(tenantId, enabled);
  }

  // -- DELETE --

  /**
   * Hides every version of a payload no scenario, atomic test or chained step uses; it goes back to
   * the Available tab and new versions no longer install themselves (F26).
   */
  public void uninstall(PayloadIdentity identity) {
    List<Payload> versions = payloadVersionService.findVersions(identity);
    if (!identity.isManaged() || versions.isEmpty()) {
      throw new ElementNotFoundException("No installed version of this payload");
    }
    List<String> usedBy =
        versions.stream()
            .map(injectorContractService::findByPayload)
            .filter(Objects::nonNull)
            .map(InjectorContract::getId)
            .flatMap(contractId -> payloadVersionChangeService.usages(contractId).stream())
            .map(usage -> usage.scenarioName() != null ? usage.scenarioName() : usage.name())
            .distinct()
            .toList();
    if (!usedBy.isEmpty()) {
      throw new BadRequestException("Still used by: " + String.join(", ", usedBy));
    }
    payloadVersionService.setUninstalled(identity, true);
    versions.forEach(contentHistoryService::payloadUninstalled);
  }

  /** Same, by external ID: refused when several repositories have it. */
  public void uninstall(String externalId) {
    List<PayloadIdentity> identities = payloadVersionService.findManagedIdentities(externalId);
    if (identities.size() != 1) {
      throw new BadRequestException(
          identities.isEmpty()
              ? "No installed version of this payload"
              : "Several repositories have " + externalId + ": uninstall it from its action");
    }
    uninstall(identities.getFirst());
  }

  /** Same, from any of its versions: the Threat Arsenal card knows the payload, not its ID. */
  public void uninstallByPayload(String payloadId) {
    Payload payload = payloadVersionService.findById(payloadId);
    if (!PayloadVersionService.isManaged(payload)) {
      throw new BadRequestException("Only a catalogue payload can be uninstalled");
    }
    uninstall(payload.getIdentity());
  }

  // -- HELPERS --

  // A payload item names its own content hash among its payload references.
  private String contentHash(CatalogItem item) {
    return catalogService.payloadRefs(item).stream()
        .filter(ref -> item.getExternalId().equals(ref.externalId()))
        .map(PayloadRef::sha256)
        .filter(Objects::nonNull)
        .findFirst()
        .orElseThrow(() -> new BadRequestException("The release gives no content hash for it"));
  }

  private List<CatalogItem> latestPerExternalId() {
    return catalogService.findAll(ContentType.PAYLOAD).stream()
        .collect(Collectors.groupingBy(CatalogItem::getExternalId))
        .values()
        .stream()
        .map(
            items ->
                items.stream()
                    .max(Comparator.comparing(CatalogItem::getVersion, SemanticVersion.ORDER))
                    .orElseThrow())
        .toList();
  }

  private record Released(String externalId, String version) {}

  // Payload items of older manifests, then the payload versions a release lists apart.
  private static List<Released> releasedPayloads(CatalogManifest manifest) {
    Stream<Released> items =
        Optional.ofNullable(manifest.items()).orElse(List.of()).stream()
            .filter(item -> item.type() != null && "payload".equalsIgnoreCase(item.type()))
            .map(item -> new Released(item.externalId(), item.version()));
    Stream<Released> versions =
        Optional.ofNullable(manifest.payloads()).orElse(List.of()).stream()
            .map(version -> new Released(version.openaevId(), version.version()));
    return Stream.concat(items, versions)
        .filter(released -> released.externalId() != null && isVersion(released.version()))
        .sorted(Comparator.comparing(Released::version, SemanticVersion.ORDER))
        .toList();
  }

  private static boolean isVersion(String version) {
    try {
      SemanticVersion.parse(version);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isLatest(Released item, CatalogManifest manifest) {
    return releasedPayloads(manifest).stream()
        .filter(other -> other.externalId().equals(item.externalId()))
        .map(Released::version)
        .max(SemanticVersion.ORDER)
        .map(item.version()::equals)
        .orElse(true);
  }
}
