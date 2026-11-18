package io.openaev.service.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.database.model.catalog.CatalogItemStatus;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.FileService;
import io.openaev.utils.catalog.SemanticVersion;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Checks each file of a release against its manifest and stores the valid ones once. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogFileService {

  // The size bound of a payload version file in the scenarios repository.
  private static final long MAX_VERSION_FILE_SIZE = 262_144;
  private static final long MAX_RELEASE_SIZE = 512L * 1024 * 1024;

  private final FileService fileService;
  private final ObjectMapper mapper;

  /**
   * Reads the files of a release, refusing it before any read when a file is not in the manifest,
   * is larger than the manifest says, or the release is too large.
   */
  public Map<String, byte[]> read(CatalogManifest manifest, List<MultipartFile> files) {
    Map<String, Long> declared = new HashMap<>();
    manifest.items().stream()
        .filter(item -> item.archive() != null)
        .forEach(item -> declared.put(item.archive().name(), item.archive().size()));
    manifest.payloadVersions().stream()
        .filter(version -> version.file() != null)
        .forEach(version -> declared.put(version.file().name(), version.file().size()));
    long total = 0;
    for (MultipartFile file : files) {
      String name = file.getOriginalFilename();
      if (!declared.containsKey(name)) {
        throw new BadRequestException("A file of the release is not in its manifest: " + name);
      }
      Long size = declared.get(name);
      if (size != null && file.getSize() > size) {
        throw new BadRequestException(
            "A file of the release is larger than its manifest says: " + name);
      }
      total += file.getSize();
    }
    if (total > MAX_RELEASE_SIZE) {
      throw new BadRequestException("The release is larger than " + MAX_RELEASE_SIZE + " bytes");
    }
    Map<String, byte[]> archives = new HashMap<>();
    for (MultipartFile file : files) {
      try {
        archives.put(file.getOriginalFilename(), file.getBytes());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return archives;
  }

  /**
   * Checks each archive against the manifest and stores the valid ones once for every tenant.
   * Returns why each refused version was refused, and the payload files read.
   */
  public CheckedRelease verifyAndStore(CatalogManifest manifest, Map<String, byte[]> archives) {
    boolean unnamed =
        manifest.items().stream().anyMatch(i -> i.externalId() == null || i.version() == null)
            || manifest.payloadVersions().stream()
                .anyMatch(v -> v.openaevId() == null || v.version() == null);
    if (unnamed) {
      throw new BadRequestException("Manifest entry without an ID or a version");
    }
    Map<String, String> refusals = new HashMap<>();
    Map<String, JsonNode> payloadFiles = new HashMap<>();
    for (CatalogManifest.Item item : manifest.items()) {
      String key = CatalogManifest.key(item.externalId(), item.version());
      Optional<String> reason = check(item, archives);
      if (reason.isPresent()) {
        log.warn("Catalogue item {} refused: {}", key, reason.get());
        refusals.put(key, reason.get());
        continue;
      }
      store(item.archive().sha256(), archives.get(item.archive().name()));
    }
    for (CatalogManifest.PayloadVersion version : manifest.payloadVersions()) {
      String key = CatalogManifest.key(version.openaevId(), version.version());
      Optional<String> reason = check(version, archives);
      if (reason.isPresent()) {
        log.warn("Catalogue payload {} refused: {}", key, reason.get());
        refusals.put(key, reason.get());
        continue;
      }
      byte[] file = archives.get(version.file().name());
      store(version.file().sha256(), file);
      payloadFiles.put(key, readVersionFile(file));
    }
    return new CheckedRelease(manifest, refusals, payloadFiles);
  }

  // A storage failure stops the whole release, so the next send retries it.
  private void store(String sha256, byte[] content) {
    try {
      if (!fileService.catalogPackageExists(sha256)) {
        fileService.uploadCatalogPackage(sha256, new ByteArrayInputStream(content));
      }
    } catch (Exception e) {
      throw new IllegalStateException("A catalogue archive could not be stored", e);
    }
  }

  private static Optional<String> check(CatalogManifest.Item item, Map<String, byte[]> archives) {
    if (item.type() == null || item.archive() == null || item.archive().sha256() == null) {
      return Optional.of("Incomplete manifest entry");
    }
    try {
      if (item.contentType() != ContentType.SCENARIO) {
        return Optional.of("Payloads are sent in the payloads list");
      }
    } catch (IllegalArgumentException e) {
      return Optional.of("Unknown type or version format");
    }
    if (!SemanticVersion.isValid(item.version())) {
      return Optional.of("Unknown type or version format");
    }
    try {
      CatalogItemStatus.fromManifest(item.status());
    } catch (IllegalArgumentException e) {
      return Optional.of("Unknown status");
    }
    byte[] archive = archives.get(item.archive().name());
    if (archive == null) {
      return Optional.of("Archive missing from the release");
    }
    if (item.archive().size() != null && archive.length != item.archive().size()) {
      return Optional.of("Archive size does not match the manifest");
    }
    if (!CatalogArchiveChecks.sha256(archive).equalsIgnoreCase(item.archive().sha256())) {
      return Optional.of("Archive hash does not match the manifest");
    }
    return CatalogArchiveChecks.unsafeReason(archive);
  }

  private Optional<String> check(
      CatalogManifest.PayloadVersion version, Map<String, byte[]> files) {
    if (version.sha256() == null
        || version.file() == null
        || version.file().name() == null
        || version.file().sha256() == null) {
      return Optional.of("Incomplete manifest entry");
    }
    if (!SemanticVersion.isValid(version.version())) {
      return Optional.of("Unknown version format");
    }
    try {
      CatalogItemStatus.fromManifest(version.status());
    } catch (IllegalArgumentException e) {
      return Optional.of("Unknown status");
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
}
