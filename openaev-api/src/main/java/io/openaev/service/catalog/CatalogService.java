package io.openaev.service.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.CatalogItemOutput;
import io.openaev.api.catalog.CatalogManifest;
import io.openaev.api.catalog.CatalogSyncOutput;
import io.openaev.api.catalog.CatalogSyncOutput.Refusal;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogItem;
import io.openaev.database.model.CatalogSyncState;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.database.repository.CatalogSyncStateRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.FileService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.SemanticVersion;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The content catalogue of a tenant (F16): releases sent by the collector, checked as RFC 0004 §4.6
 * requires, then listed for the Available tabs. The collector only transports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

  private final CatalogItemRepository catalogItemRepository;
  private final CatalogSyncStateRepository catalogSyncStateRepository;
  private final FileService fileService;
  private final ScenarioService scenarioService;
  private final PayloadVersionService payloadVersionService;
  private final CatalogRequirementsService catalogRequirementsService;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;

  // -- LIST --

  /** Every catalogue item of this type, as its latest version plus all versions and installs. */
  @Transactional(readOnly = true)
  public List<CatalogItemOutput> list(ContentType type) {
    Map<String, Map<String, String>> installedScenarios =
        type == ContentType.SCENARIO ? scenarioService.findInstalledCatalogVersions() : Map.of();
    CatalogRequirementsService.Checker requirements = catalogRequirementsService.checker();
    Map<String, Set<String>> installed =
        type == ContentType.SCENARIO
            ? installedScenarios.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().keySet()))
            : payloadVersionService.findInstalledManagedVersions();
    return catalogItemRepository.findAllByType(type).stream()
        .collect(Collectors.groupingBy(CatalogItem::getExternalId))
        .values()
        .stream()
        .map(
            items -> {
              List<CatalogItem> sorted =
                  items.stream()
                      .sorted(
                          Comparator.comparing(CatalogItem::getVersion, SemanticVersion.ORDER)
                              .reversed())
                      .toList();
              CatalogItem latest = sorted.getFirst();
              List<String> versions = sorted.stream().map(CatalogItem::getVersion).toList();
              List<String> installedVersions =
                  versions.stream()
                      .filter(
                          v -> installed.getOrDefault(latest.getExternalId(), Set.of()).contains(v))
                      .toList();
              Map<String, String> scenarioIds =
                  installedScenarios.getOrDefault(latest.getExternalId(), Map.of());
              return CatalogItemOutput.of(
                  latest,
                  versions,
                  installedVersions,
                  installedVersions.stream()
                      .filter(scenarioIds::containsKey)
                      .map(v -> new CatalogItemOutput.InstalledScenario(v, scenarioIds.get(v)))
                      .toList(),
                  requirements.missingPrerequisites(latest));
            })
        .sorted(
            Comparator.comparing(CatalogItemOutput::name, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  /** Every catalogue version of one payload, newest first. */
  @Transactional(readOnly = true)
  public List<CatalogItemOutput> listPayloadVersions(String externalId) {
    List<CatalogItem> sorted =
        catalogItemRepository.findAllByTypeAndExternalId(ContentType.PAYLOAD, externalId).stream()
            .sorted(Comparator.comparing(CatalogItem::getVersion, SemanticVersion.ORDER).reversed())
            .toList();
    List<String> versions = sorted.stream().map(CatalogItem::getVersion).toList();
    Set<String> installed =
        payloadVersionService.findInstalledManagedVersions().getOrDefault(externalId, Set.of());
    List<String> installedVersions = versions.stream().filter(installed::contains).toList();
    CatalogRequirementsService.Checker requirements = catalogRequirementsService.checker();
    return sorted.stream()
        .map(
            item ->
                CatalogItemOutput.of(
                    item,
                    versions,
                    installedVersions,
                    List.of(),
                    requirements.missingPrerequisites(item)))
        .toList();
  }

  @Transactional(readOnly = true)
  /**
   * A released version. Without a repository, the one repository that has it: several are
   * ambiguous, since an external ID is only unique within its repository.
   */
  public Optional<CatalogItem> find(
      ContentType type, String repository, String externalId, String version) {
    return findItem(type, repository, externalId, version);
  }

  private Optional<CatalogItem> findItem(
      ContentType type, String repository, String externalId, String version) {
    if (repository != null) {
      return catalogItemRepository.findByTypeAndRepositoryAndExternalIdAndVersion(
          type, repository, externalId, version);
    }
    List<CatalogItem> items =
        catalogItemRepository.findAllByTypeAndExternalIdAndVersion(type, externalId, version);
    if (items.size() > 1) {
      throw new BadRequestException(
          "Several repositories release " + externalId + " " + version + ": name the repository");
    }
    return items.stream().findFirst();
  }

  public Optional<CatalogItem> find(ContentType type, String externalId, String version) {
    return findItem(type, null, externalId, version);
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> findAll(ContentType type) {
    return catalogItemRepository.findAllByType(type);
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> findVersions(ContentType type, String externalId) {
    return catalogItemRepository.findAllByTypeAndExternalId(type, externalId);
  }

  @Transactional(readOnly = true)
  public List<CatalogItem> findVersions(ContentType type, String repository, String externalId) {
    return catalogItemRepository.findAllByTypeAndRepositoryAndExternalId(
        type, repository, externalId);
  }

  /** The stored package, refused unless it still has the hash its release gave. */
  public byte[] readVerifiedPackage(CatalogItem item) {
    byte[] archive =
        readPackage(item)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue package not found"));
    if (!CatalogArchiveChecks.sha256(archive).equalsIgnoreCase(item.getArchiveHash())) {
      throw new BadRequestException("The stored package does not match its release");
    }
    return archive;
  }

  /** The payload versions an item names, with their content hash. */
  public List<CatalogManifest.PayloadRef> payloadRefs(CatalogItem item) {
    if (item.getPayloadRefs() == null) {
      return List.of();
    }
    try {
      return mapper.readValue(
          item.getPayloadRefs(), new TypeReference<List<CatalogManifest.PayloadRef>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Unreadable payload references", e);
    }
  }

  /** The stored package of a catalogue item, as released. */
  public Optional<byte[]> readPackage(CatalogItem item) {
    return fileService
        .getCatalogPackage(item.getArchiveHash())
        .map(
            stream -> {
              try (stream) {
                return stream.readAllBytes();
              } catch (Exception e) {
                throw new IllegalStateException("Unreadable catalogue package", e);
              }
            });
  }

  // -- SYNC --

  /**
   * Checks each archive against the manifest and stores the valid ones once for every tenant. Runs
   * outside any transaction: storage is network I/O. Returns why each refused item was refused.
   */
  public Map<String, String> verifyAndStore(
      CatalogManifest manifest, Map<String, byte[]> archives) {
    Map<String, String> refusals = new HashMap<>();
    for (CatalogManifest.Item item : items(manifest)) {
      String key = key(item.externalId(), item.version());
      Optional<String> reason = check(item, archives);
      if (reason.isPresent()) {
        log.warn("Catalogue item {} refused: {}", key, reason.get());
        refusals.put(key, reason.get());
        continue;
      }
      String sha256 = item.archive().sha256();
      try {
        if (fileService.getCatalogPackage(sha256).isEmpty()) {
          fileService.uploadCatalogPackage(
              sha256, new ByteArrayInputStream(archives.get(item.archive().name())));
        }
      } catch (Exception e) {
        log.warn("Catalogue item {} could not be stored", key, e);
        refusals.put(key, "Archive could not be stored");
      }
    }
    return refusals;
  }

  /** Records the release in the tenant's catalogue; an item failing its checks is skipped alone. */
  @Transactional(rollbackFor = Exception.class)
  public CatalogSyncOutput apply(
      // Unused by the body; TenantScopeTransactionAspect reads it to scope this transaction.
      TxCtx ctx, String tenantId, CatalogManifest manifest, Map<String, String> refusals) {
    CatalogSyncState state =
        catalogSyncStateRepository
            .findByRepositoryAndChannel(manifest.repository(), channel(manifest))
            .orElse(null);
    if (state != null && Objects.equals(state.getRelease(), manifest.release())) {
      return new CatalogSyncOutput(manifest.release(), false, 0, 0, List.of());
    }
    Tenant tenant = entityManager.getReference(Tenant.class, tenantId);
    List<Refusal> refused = new ArrayList<>();
    int added = 0;
    int unchanged = 0;
    for (CatalogManifest.Item item : items(manifest)) {
      String reason = refusals.get(key(item.externalId(), item.version()));
      if (reason != null) {
        refused.add(new Refusal(item.externalId(), item.version(), reason));
        continue;
      }
      ContentType type = item.contentType();
      Optional<CatalogItem> existing =
          catalogItemRepository.findByTypeAndRepositoryAndExternalIdAndVersion(
              type, manifest.repository(), item.externalId(), item.version());
      if (existing.isPresent()
          && !existing.get().getArchiveHash().equalsIgnoreCase(item.archive().sha256())) {
        refused.add(
            new Refusal(
                item.externalId(),
                item.version(),
                "A published version cannot change its content"));
        continue;
      }
      CatalogItem catalogItem = existing.orElseGet(CatalogItem::new);
      if (existing.isEmpty()) {
        catalogItem.setType(type);
        catalogItem.setExternalId(item.externalId());
        catalogItem.setVersion(item.version());
        catalogItem.setArchiveHash(item.archive().sha256().toLowerCase(Locale.ROOT));
        catalogItem.setTenant(tenant);
        added++;
      } else {
        unchanged++;
      }
      fill(catalogItem, item, manifest);
      catalogItemRepository.save(catalogItem);
    }
    Set<String> released =
        items(manifest).stream()
            .map(item -> key(item.externalId(), item.version()))
            .collect(Collectors.toSet());
    catalogItemRepository.findAllByRepository(manifest.repository()).stream()
        .filter(item -> !released.contains(key(item.getExternalId(), item.getVersion())))
        .forEach(catalogItemRepository::delete);
    if (state == null) {
      state = new CatalogSyncState();
      state.setRepository(manifest.repository());
      state.setChannel(channel(manifest));
      state.setTenant(tenant);
    }
    state.setRelease(manifest.release());
    catalogSyncStateRepository.save(state);
    return new CatalogSyncOutput(manifest.release(), true, added, unchanged, refused);
  }

  private void fill(CatalogItem target, CatalogManifest.Item item, CatalogManifest manifest) {
    target.setStatus(item.status() == null ? "VERIFIED" : item.status());
    target.setName(item.name());
    target.setDescription(item.description());
    target.setAuthor(item.author());
    target.setSeverity(item.severity());
    target.setCategory(item.category());
    target.setMainFocus(item.mainFocus());
    target.setScenarioType(item.scenarioTypeOrType());
    target.setPlatforms(
        item.platforms() == null ? new String[0] : item.platforms().toArray(new String[0]));
    target.setInjectsCount(item.injectsCount());
    target.setChangelog(item.changelog());
    target.setPublishedAt(item.publishedAt() == null ? manifest.issuedAt() : item.publishedAt());
    target.setArchiveName(item.archive().name());
    try {
      target.setRequires(
          item.requires() == null ? null : mapper.writeValueAsString(item.requires()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Unreadable requirements", e);
    }
    target.setRepository(manifest.repository());
    target.setRelease(manifest.release());
    try {
      target.setPayloadRefs(
          mapper.writeValueAsString(item.payloads() == null ? List.of() : item.payloads()));
    } catch (Exception e) {
      throw new IllegalArgumentException("Unreadable payload references", e);
    }
  }

  private static Optional<String> check(CatalogManifest.Item item, Map<String, byte[]> archives) {
    if (item.type() == null
        || item.externalId() == null
        || item.version() == null
        || item.archive() == null
        || item.archive().sha256() == null) {
      return Optional.of("Incomplete manifest entry");
    }
    try {
      item.contentType();
      SemanticVersion.parse(item.version());
    } catch (Exception e) {
      return Optional.of("Unknown type or version format");
    }
    byte[] archive = archives.get(item.archive().name());
    if (archive == null) {
      return Optional.of("Archive missing from the release");
    }
    if (!CatalogArchiveChecks.sha256(archive).equalsIgnoreCase(item.archive().sha256())) {
      return Optional.of("Archive hash does not match the manifest");
    }
    return CatalogArchiveChecks.unsafeReason(archive);
  }

  private static List<CatalogManifest.Item> items(CatalogManifest manifest) {
    return manifest.items() == null ? List.of() : manifest.items();
  }

  private static String channel(CatalogManifest manifest) {
    return manifest.channel() == null ? "stable" : manifest.channel();
  }

  private static String key(String externalId, String version) {
    return externalId + "@" + version;
  }
}
