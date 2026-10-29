package io.openaev.service.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.FileService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.SemanticVersion;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  // The size bound of a payload version file in the scenarios repository.
  private static final long MAX_VERSION_FILE_SIZE = 262_144;

  private enum Saved {
    ADDED,
    UNCHANGED,
    CHANGED
  }

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
    // Payload names by external id and version, for the payloads a scenario runs.
    Map<String, String> payloadNames =
        type == ContentType.SCENARIO
            ? catalogItemRepository.findAllByType(ContentType.PAYLOAD).stream()
                .collect(
                    Collectors.toMap(
                        p -> p.getExternalId() + ":" + p.getVersion(),
                        p -> Optional.ofNullable(p.getName()).orElse(p.getExternalId()),
                        (x, y) -> x))
            : Map.of();
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
                  requirements.missingPrerequisites(latest),
                  type == ContentType.SCENARIO
                      ? payloadRefs(latest).stream()
                          .map(
                              ref ->
                                  new CatalogItemOutput.PayloadUsed(
                                      ref.externalId(),
                                      ref.version(),
                                      payloadNames.get(ref.externalId() + ":" + ref.version())))
                          .toList()
                      : List.of());
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

  /** The content of a catalogue payload version, readable before it is installed. */
  @Transactional(readOnly = true)
  public JsonNode readPayloadVersionContent(String externalId, String version) {
    CatalogItem item =
        find(ContentType.PAYLOAD, externalId, version)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue payload version not found"));
    byte[] content = readVerifiedPackage(item);
    if (!CatalogArchiveChecks.isVersionFile(content)) {
      throw new ElementNotFoundException("This version is released as an archive");
    }
    return readVersionFile(content);
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
      store(item.archive().sha256(), archives.get(item.archive().name()), key, refusals);
    }
    for (CatalogManifest.PayloadVersion version : payloadVersions(manifest)) {
      String key = key(version.openaevId(), version.version());
      Optional<String> reason = check(version, archives);
      if (reason.isPresent()) {
        log.warn("Catalogue payload {} refused: {}", key, reason.get());
        refusals.put(key, reason.get());
        continue;
      }
      store(version.file().sha256(), archives.get(version.file().name()), key, refusals);
    }
    return refusals;
  }

  private void store(String sha256, byte[] content, String key, Map<String, String> refusals) {
    try {
      if (fileService.getCatalogPackage(sha256).isEmpty()) {
        fileService.uploadCatalogPackage(sha256, new ByteArrayInputStream(content));
      }
    } catch (Exception e) {
      log.warn("Catalogue item {} could not be stored", key, e);
      refusals.put(key, "Archive could not be stored");
    }
  }

  /** Records the release in the tenant's catalogue; an item failing its checks is skipped alone. */
  @Transactional(rollbackFor = Exception.class)
  public CatalogSyncOutput apply(
      // Unused by the body; TenantScopeTransactionAspect reads it to scope this transaction.
      TxCtx ctx,
      String tenantId,
      CatalogManifest manifest,
      Map<String, byte[]> archives,
      Map<String, String> refusals) {
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
      Saved saved =
          save(
              tenant,
              item.contentType(),
              manifest.repository(),
              item.externalId(),
              item.version(),
              item.archive().sha256(),
              target -> fill(target, item, manifest));
      switch (saved) {
        case ADDED -> added++;
        case UNCHANGED -> unchanged++;
        case CHANGED -> refused.add(changed(item.externalId(), item.version()));
      }
    }
    for (CatalogManifest.PayloadVersion version : payloadVersions(manifest)) {
      String reason = refusals.get(key(version.openaevId(), version.version()));
      if (reason != null) {
        refused.add(new Refusal(version.openaevId(), version.version(), reason));
        continue;
      }
      JsonNode file = readVersionFile(archives.get(version.file().name()));
      Saved saved =
          save(
              tenant,
              ContentType.PAYLOAD,
              manifest.repository(),
              version.openaevId(),
              version.version(),
              version.file().sha256(),
              target -> fill(target, version, file, manifest));
      switch (saved) {
        case ADDED -> added++;
        case UNCHANGED -> unchanged++;
        case CHANGED -> refused.add(changed(version.openaevId(), version.version()));
      }
    }
    Set<String> released =
        Stream.concat(
                items(manifest).stream().map(item -> key(item.externalId(), item.version())),
                payloadVersions(manifest).stream()
                    .map(version -> key(version.openaevId(), version.version())))
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

  private Saved save(
      Tenant tenant,
      ContentType type,
      String repository,
      String externalId,
      String version,
      String archiveHash,
      Consumer<CatalogItem> fill) {
    Optional<CatalogItem> existing =
        catalogItemRepository.findByTypeAndRepositoryAndExternalIdAndVersion(
            type, repository, externalId, version);
    if (existing.isPresent() && !existing.get().getArchiveHash().equalsIgnoreCase(archiveHash)) {
      return Saved.CHANGED;
    }
    CatalogItem catalogItem = existing.orElseGet(CatalogItem::new);
    if (existing.isEmpty()) {
      catalogItem.setType(type);
      catalogItem.setExternalId(externalId);
      catalogItem.setVersion(version);
      catalogItem.setArchiveHash(archiveHash.toLowerCase(Locale.ROOT));
      catalogItem.setTenant(tenant);
    }
    fill.accept(catalogItem);
    catalogItemRepository.save(catalogItem);
    return existing.isEmpty() ? Saved.ADDED : Saved.UNCHANGED;
  }

  private static Refusal changed(String externalId, String version) {
    return new Refusal(externalId, version, "A published version cannot change its content");
  }

  private void fill(
      CatalogItem target,
      CatalogManifest.PayloadVersion version,
      JsonNode file,
      CatalogManifest manifest) {
    target.setStatus("VERIFIED");
    target.setName(file.path("payload_name").asText(version.payload()));
    target.setDescription(file.path("payload_description").asText(null));
    List<String> platforms = new ArrayList<>();
    file.path("payload_platforms").forEach(platform -> platforms.add(platform.asText()));
    target.setPlatforms(platforms.toArray(new String[0]));
    target.setCategory(primaryDomain(file));
    target.setPublishedAt(manifest.issuedAt());
    target.setArchiveName(version.file().name());
    target.setRepository(manifest.repository());
    target.setRelease(manifest.release());
    try {
      target.setPayloadRefs(
          mapper.writeValueAsString(
              List.of(
                  new CatalogManifest.PayloadRef(
                      version.openaevId(), version.version(), version.sha256()))));
    } catch (Exception e) {
      throw new IllegalArgumentException("Unreadable payload references", e);
    }
  }

  // Same rule as the Threat Arsenal sections: the first domain by name.
  private static String primaryDomain(JsonNode file) {
    List<String> names = new ArrayList<>();
    file.path("payload_domains").forEach(domain -> names.add(domain.path("domain_name").asText()));
    return names.stream()
        .filter(name -> !name.isBlank() && !DomainService.TO_CLASSIFY.equals(name))
        .sorted()
        .findFirst()
        .orElse(null);
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

  private Optional<String> check(
      CatalogManifest.PayloadVersion version, Map<String, byte[]> files) {
    if (version.openaevId() == null
        || version.version() == null
        || version.sha256() == null
        || version.file() == null
        || version.file().name() == null
        || version.file().sha256() == null) {
      return Optional.of("Incomplete manifest entry");
    }
    try {
      SemanticVersion.parse(version.version());
    } catch (Exception e) {
      return Optional.of("Unknown version format");
    }
    byte[] file = files.get(version.file().name());
    if (file == null) {
      return Optional.of("Payload file missing from the release");
    }
    if (file.length > MAX_VERSION_FILE_SIZE
        || (version.file().size() != null && file.length != version.file().size())) {
      return Optional.of("Payload file size does not match the manifest");
    }
    if (!CatalogArchiveChecks.sha256(file).equalsIgnoreCase(version.file().sha256())) {
      return Optional.of("Payload file hash does not match the manifest");
    }
    try {
      if (!version.openaevId().equals(readVersionFile(file).path("payload_external_id").asText())) {
        return Optional.of("Payload file does not hold the listed ID");
      }
    } catch (Exception e) {
      return Optional.of("Unreadable payload file");
    }
    return Optional.empty();
  }

  private JsonNode readVersionFile(byte[] file) {
    try {
      return mapper.readTree(file);
    } catch (Exception e) {
      throw new IllegalArgumentException("Unreadable payload file", e);
    }
  }

  private static List<CatalogManifest.PayloadVersion> payloadVersions(CatalogManifest manifest) {
    return manifest.payloads() == null ? List.of() : manifest.payloads();
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
