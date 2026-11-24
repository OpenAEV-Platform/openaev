package io.openaev.service.catalog;

import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.CatalogSyncOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogRelease;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.database.repository.CatalogReleaseRepository;
import io.openaev.notification.catalog.CatalogEvents.AddedVersion;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.scenario.CatalogScenarioService;
import io.openaev.utils.catalog.SemanticVersion;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a release in the catalogue of a tenant. */
@Service
@RequiredArgsConstructor
public class CatalogService {

  private final CatalogItemRepository catalogItemRepository;
  private final CatalogScenarioService catalogScenarioService;
  private final CatalogReleaseRepository catalogReleaseRepository;
  private final CatalogItemMapper catalogItemMapper;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Records the release in the tenant's catalogue; a version failing its checks is skipped alone.
   */
  @Transactional(rollbackFor = Exception.class)
  public CatalogSyncOutput apply(
      // Unused by the body; TenantScopeTransactionAspect reads it to scope this transaction.
      TxCtx ctx, String tenantId, CheckedRelease checked) {
    CatalogManifest manifest = checked.manifest();
    CatalogRelease latest = latestRelease(manifest);
    if (isLatest(latest, manifest)) {
      return CatalogSyncOutput.alreadyApplied(manifest.release(), manifest.releaseApprover());
    }
    refuseIfOlder(latest, manifest);
    CatalogRelease release = saveRelease(tenantId, manifest);
    CatalogSyncResult result = saveItems(release, checked, latest == null);
    removeItemsNotIn(tenantId, release, manifest);
    markLatestOffered(release);
    eventPublisher.publishEvent(result.toEvent(tenantId));
    return result.toOutput();
  }

  /** Whether this release is the last one applied, checked before its files are read again. */
  @Transactional(readOnly = true)
  public boolean isApplied(
      // Unused by the body; TenantScopeTransactionAspect reads it to scope this transaction.
      TxCtx ctx, CatalogManifest manifest) {
    return isLatest(latestRelease(manifest), manifest);
  }

  // -- RELEASE --

  private static boolean isLatest(CatalogRelease latest, CatalogManifest manifest) {
    return latest != null && latest.getName().equals(manifest.release());
  }

  private CatalogRelease latestRelease(CatalogManifest manifest) {
    return catalogReleaseRepository
        .findFirstByRepositoryOrderByCreatedAtDesc(manifest.repository())
        .orElse(null);
  }

  private static void refuseIfOlder(CatalogRelease latest, CatalogManifest manifest) {
    if (latest != null && SemanticVersion.ORDER.compare(manifest.release(), latest.getName()) < 0) {
      throw new BadRequestException(
          "Release "
              + manifest.release()
              + " is older than "
              + latest.getName()
              + ", the last one applied");
    }
  }

  private CatalogRelease saveRelease(String tenantId, CatalogManifest manifest) {
    CatalogRelease release = new CatalogRelease();
    release.setRepository(manifest.repository());
    release.setName(manifest.release());
    release.setTenant(entityManager.getReference(Tenant.class, tenantId));
    return catalogReleaseRepository.save(release);
  }

  // -- ITEMS --

  private CatalogSyncResult saveItems(
      CatalogRelease release, CheckedRelease checked, boolean firstSync) {
    CatalogManifest manifest = checked.manifest();
    CatalogSyncResult result = new CatalogSyncResult(manifest, firstSync);
    for (CatalogManifest.Item item : manifest.items()) {
      itemToSave(
              release,
              checked,
              result,
              CatalogScenario.class,
              item.externalId(),
              item.version(),
              item.archive().sha256(),
              CatalogScenario::new)
          .ifPresent(
              scenario -> {
                catalogItemMapper.fill(scenario, item, manifest);
                save(scenario, result);
              });
    }
    for (CatalogManifest.PayloadVersion version : manifest.payloadVersions()) {
      itemToSave(
              release,
              checked,
              result,
              CatalogPayload.class,
              version.openaevId(),
              version.version(),
              version.file().sha256(),
              CatalogPayload::new)
          .ifPresent(
              payload -> {
                catalogItemMapper.fill(payload, version, checked.payloadFile(version), manifest);
                save(payload, result);
              });
    }
    return result;
  }

  /**
   * The item to fill for a version: the known one, or a new one. Empty when the version is refused:
   * its files failed their checks, or a published version came back with another content.
   */
  private <T extends CatalogItem> Optional<T> itemToSave(
      CatalogRelease release,
      CheckedRelease checked,
      CatalogSyncResult result,
      Class<T> type,
      String externalId,
      String version,
      String archiveHash,
      Supplier<T> create) {
    Optional<String> refusal = checked.refusal(externalId, version);
    if (refusal.isPresent()) {
      result.refused(externalId, version, refusal.get());
      return Optional.empty();
    }
    Optional<T> known = catalogItemRepository.findVersion(type, externalId, version);
    if (known.isPresent()) {
      if (!known.get().getArchiveHash().equalsIgnoreCase(archiveHash)) {
        result.refused(externalId, version, "A published version cannot change its content");
        return Optional.empty();
      }
      return known;
    }
    T item = create.get();
    item.setExternalId(externalId);
    item.setVersion(version);
    item.setArchiveHash(archiveHash.toLowerCase(Locale.ROOT));
    item.setRelease(release);
    item.setTenant(release.getTenant());
    return Optional.of(item);
  }

  private void save(CatalogItem item, CatalogSyncResult result) {
    if (item.getId() != null) {
      catalogItemRepository.save(item);
      result.unchanged();
      return;
    }
    boolean isNew =
        result.firstSync()
            || !catalogItemRepository.existsByTypeAndExternalId(
                item.getType(), item.getExternalId());
    catalogItemRepository.save(item);
    result.added(
        new AddedVersion(
            item.getType(),
            item.getRelease().getRepository(),
            item.getExternalId(),
            item.getName(),
            item.getVersion(),
            isNew));
  }

  // A version a scenario still runs stays, unlisted: updating or rolling it back reads it.
  private void removeItemsNotIn(String tenantId, CatalogRelease release, CatalogManifest manifest) {
    Set<String> listed = manifest.keys();
    Set<String> installed =
        catalogScenarioService.installedVersionKeys(tenantId, release.getRepository());
    List<CatalogItem> removed = new ArrayList<>();
    for (CatalogItem item :
        catalogItemRepository.findAllByReleaseRepository(release.getRepository())) {
      String key = CatalogManifest.key(item.getExternalId(), item.getVersion());
      if (listed.contains(key)) {
        continue;
      }
      if (installed.contains(key)) {
        item.setStatus(CatalogItemStatus.UNLISTED);
      } else {
        removed.add(item);
      }
    }
    catalogItemRepository.deleteAll(removed);
  }

  // Each item now offers its latest version not withdrawn.
  private void markLatestOffered(CatalogRelease release) {
    List<CatalogItem> items =
        catalogItemRepository.findAllByReleaseRepository(release.getRepository());
    Set<String> latest =
        items.stream().collect(Collectors.groupingBy(CatalogItem::getType)).values().stream()
            .flatMap(sameType -> CatalogListService.latestVersions(sameType).stream())
            .map(CatalogItem::getId)
            .collect(Collectors.toSet());
    items.forEach(item -> item.setLatestOffered(latest.contains(item.getId())));
  }
}
