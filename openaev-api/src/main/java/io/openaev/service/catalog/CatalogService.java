package io.openaev.service.catalog;

import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.CatalogSyncOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogRelease;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.database.repository.CatalogReleaseRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.catalog.SemanticVersion;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records a release in the catalogue of a tenant. */
@Service
@RequiredArgsConstructor
public class CatalogService {

  private final CatalogItemRepository catalogItemRepository;
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
    if (latest != null && latest.getName().equals(manifest.release())) {
      return CatalogSyncOutput.alreadyApplied(manifest.release(), manifest.releaseApprover());
    }
    refuseIfOlder(latest, manifest);
    CatalogRelease release = saveRelease(tenantId, manifest);
    CatalogSyncResult result = saveItems(release, checked);
    removeItemsNotIn(release, manifest);
    eventPublisher.publishEvent(result.toEvent(tenantId));
    return result.toOutput();
  }

  // -- RELEASE --

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

  private CatalogSyncResult saveItems(CatalogRelease release, CheckedRelease checked) {
    CatalogManifest manifest = checked.manifest();
    CatalogSyncResult result = new CatalogSyncResult(manifest);
    for (CatalogManifest.Item item : manifest.items()) {
      itemToSave(
              release,
              checked,
              result,
              ContentType.SCENARIO,
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
              ContentType.PAYLOAD,
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
  // The finder filters on the type, so the item found is of the class created for it.
  @SuppressWarnings("unchecked")
  private <T extends CatalogItem> Optional<T> itemToSave(
      CatalogRelease release,
      CheckedRelease checked,
      CatalogSyncResult result,
      ContentType type,
      String externalId,
      String version,
      String archiveHash,
      Supplier<T> create) {
    Optional<String> refusal = checked.refusal(externalId, version);
    if (refusal.isPresent()) {
      result.refused(externalId, version, refusal.get());
      return Optional.empty();
    }
    Optional<CatalogItem> known =
        catalogItemRepository.findByTypeAndExternalIdAndVersion(type, externalId, version);
    if (known.isPresent()) {
      if (!known.get().getArchiveHash().equalsIgnoreCase(archiveHash)) {
        result.refused(externalId, version, "A published version cannot change its content");
        return Optional.empty();
      }
      return Optional.of((T) known.get());
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
    boolean added = item.getId() == null;
    catalogItemRepository.save(item);
    if (added) {
      result.added();
    } else {
      result.unchanged();
    }
  }

  private void removeItemsNotIn(CatalogRelease release, CatalogManifest manifest) {
    Set<String> listed = manifest.keys();
    List<CatalogItem> removed =
        catalogItemRepository.findAllByReleaseRepository(release.getRepository()).stream()
            .filter(
                item ->
                    !listed.contains(CatalogManifest.key(item.getExternalId(), item.getVersion())))
            .toList();
    catalogItemRepository.deleteAll(removed);
  }
}
