package io.openaev.service;

import com.google.common.annotations.VisibleForTesting;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.multitenancy.MinioException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class MinioService implements DependenciesManager {

  /**
   * Reserved root-level prefix for platform-scoped assets shared across all tenants (catalog
   * connector logos, ...). Objects under this prefix are intentionally NOT tenant-prefixed and must
   * never be moved into a tenant path.
   */
  public static final String PLATFORM_PATH_PREFIX = "platform/";

  private static final int BATCH_DELETE_SIZE = 1000;

  private final MinioConfig minioConfig;
  private final MinioClient minioClient;

  @Override
  public void createDependencyForTenant(Tenant tenant) {
    log.info(
        "Tenant {} created — files will be stored under path prefix {}/",
        tenant.getId(),
        tenant.getId());
  }

  @Override
  public void deleteDependencyForTenant(String tenantId) throws DependenciesManagerException {
    log.info("Deleting all files for tenant {} under prefix {}/", tenantId, tenantId);
    try {
      deleteObjectsByPrefix(tenantId + "/", true);
    } catch (MinioException e) {
      throw new DependenciesManagerException(
          "Failed to delete Minio files for tenant " + tenantId, e);
    }
  }

  // -- CREATE/UPDATE --

  /**
   * Uploads a file under the path of the named tenant, the tenant of the row the object belongs to.
   *
   * @param tenantId the tenant whose path the object is written under
   * @param fileName the target file path/name in the bucket
   * @param data the input stream containing the file data
   * @param size the size of the file in bytes
   * @param contentType the MIME type of the file
   * @return the full tenant-prefixed path of the uploaded file
   * @throws Exception if the upload fails
   */
  public String uploadFileForTenant(
      String tenantId, String fileName, InputStream data, long size, String contentType)
      throws Exception {
    String objectKey = getPathForTenant(tenantId, fileName);
    minioClient.putObject(
        PutObjectArgs.builder().bucket(bucket()).object(objectKey).stream(data, size, -1)
            .contentType(contentType)
            .build());
    return objectKey;
  }

  /**
   * Uploads a stream under the path of the named tenant, the tenant of the row the object belongs
   * to.
   *
   * @param tenantId the tenant whose path the object is written under
   * @param fileName the target file path/name in the bucket
   * @param name the original filename stored as metadata
   * @param data the input stream containing the file data
   * @return the tenant-prefixed path of the uploaded file
   */
  public String uploadStreamForTenant(
      String tenantId, String fileName, String name, InputStream data) throws Exception {
    String objectKey = getPathForTenant(tenantId, fileName);
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(bucket())
            .object(objectKey)
            .userMetadata(Map.of("filename", name))
            .stream(data, -1, 10 * 1024 * 1024)
            .build());
    return objectKey;
  }

  /**
   * Uploads a stream to the platform-level path (no tenant prefix). Use for assets that are shared
   * across all tenants, such as catalog connector logos.
   *
   * @param fileName the file path/name within the platform space
   * @param name the original filename stored as metadata
   * @param data the input stream containing the file data
   * @return the full platform path of the uploaded file
   */
  public String uploadStreamInPlatformPath(String fileName, String name, InputStream data)
      throws Exception {
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(bucket())
            .object(getPlatformPath(fileName))
            .userMetadata(Map.of("filename", name))
            .stream(data, -1, 10 * 1024 * 1024)
            .build());
    return getPlatformPath(fileName);
  }

  // -- READ --

  public Optional<InputStream> getFilePathForTenant(String tenantId, String name) {
    requireTenant(tenantId);
    try {
      GetObjectResponse objectStream =
          minioClient.getObject(
              GetObjectArgs.builder()
                  .bucket(bucket())
                  .object(getPathForTenant(tenantId, name))
                  .build());
      InputStreamResource streamResource = new InputStreamResource(objectStream);
      return Optional.of(streamResource.getInputStream());
    } catch (Exception e) {
      log.info("Error during file access", e);
      return Optional.empty();
    }
  }

  /**
   * Retrieves a file from the platform-level path (no tenant prefix). Use for assets shared across
   * all tenants, such as catalog connector logos.
   *
   * @param name the file path/name within the platform space
   * @return an Optional containing the input stream, or empty if not found
   */
  public Optional<InputStream> getFilePathInPlatform(String name) {
    try {
      GetObjectResponse objectStream =
          minioClient.getObject(
              GetObjectArgs.builder().bucket(bucket()).object(getPlatformPath(name)).build());
      InputStreamResource streamResource = new InputStreamResource(objectStream);
      return Optional.of(streamResource.getInputStream());
    } catch (Exception e) {
      log.info("Platform file not found: {}", name);
      return Optional.empty();
    }
  }

  public boolean existsInPlatform(String name) {
    try {
      objectExists(getPlatformPath(name));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Reads a file container under the path of the named tenant, the tenant of the row the object
   * belongs to.
   *
   * @param tenantId the tenant whose path the object is read from
   * @param fileTarget the target file path
   * @return the file container, or empty if the object does not exist under that tenant's path
   */
  public Optional<FileContainer> getFileContainerForTenant(String tenantId, String fileTarget) {
    requireTenant(tenantId);
    try {
      StatObjectResponse response = objectExists(getPathForTenant(tenantId, fileTarget));
      String filename = response.userMetadata().get("filename");
      Optional<InputStream> inputStream = getFilePathForTenant(tenantId, fileTarget);
      FileContainer fileContainer =
          new FileContainer(filename, response.contentType(), inputStream.orElseThrow());
      return Optional.of(fileContainer);
    } catch (Exception e) {
      log.error("Error during file container access", e);
      return Optional.empty();
    }
  }

  // -- DELETE --

  /**
   * Deletes a file under the path of the named tenant, the tenant of the row the object belongs to.
   *
   * @param tenantId the tenant whose path the object is removed from
   * @param name the object name to delete
   * @throws Exception if the deletion fails
   */
  public void deleteFileForTenant(String tenantId, String name) throws Exception {
    minioClient.removeObject(
        RemoveObjectArgs.builder()
            .bucket(bucket())
            .object(getPathForTenant(tenantId, name))
            .build());
  }

  /**
   * Deletes a directory under the path of the named tenant, the tenant of the rows the objects
   * belong to.
   *
   * @param tenantId the tenant whose path the directory is removed from
   * @param directory the directory prefix to delete
   */
  public void deleteDirectoryForTenant(String tenantId, String directory) {
    requireTenant(tenantId);
    try {
      deleteObjectsByPrefix(getPathForTenant(tenantId, directory), false);
    } catch (Exception e) {
      log.error("Error deleting directory {} for tenant {}", directory, tenantId, e);
    }
  }

  // -- HELPERS --

  /**
   * Verifies the object storage is reachable, the credentials are valid and the bucket is listable.
   *
   * <p>A tenant path is only a key prefix, not an object: statting it fails whenever the tenant has
   * no file yet, and plain S3 has no directory markers at all. A one-key listing proves access
   * without requiring anything to be stored, and succeeds on an empty tenant.
   *
   * <p>The probe lists the default tenant's prefix: the probe runs on a scheduled thread that
   * belongs to no tenant, and the default tenant exists on every platform.
   *
   * <p>Takes the client as a parameter so callers (e.g. health checks) can pass one configured with
   * short timeouts.
   */
  public void checkStorageAccessible(MinioClient client) throws Exception {
    Iterator<Result<Item>> results =
        client
            .listObjects(
                ListObjectsArgs.builder()
                    .bucket(bucket())
                    .prefix(getPathForTenant(Tenant.DEFAULT_TENANT_UUID, ""))
                    .maxKeys(1)
                    .build())
            .iterator();
    // The listing is lazy and reports failures as an error Result, so it must be consumed.
    if (results.hasNext()) {
      results.next().get();
    }
  }

  /**
   * Total size of the objects stored in the bucket, in bytes, all tenants included.
   *
   * <p>Object storage exposes no aggregated size, so the whole bucket listing has to be walked:
   * this is a costly operation (one listing round-trip per 1 000 objects) and must never be called
   * on a hot path without caching.
   *
   * @return the sum of the object sizes in bytes
   */
  public long computeUsedSize() {
    long usedSize = 0L;
    for (Result<Item> result : listObjects("", false)) {
      try {
        usedSize += result.get().size();
      } catch (Exception e) {
        throw new IllegalStateException(
            "Unable to read object metadata while computing used size", e);
      }
    }
    return usedSize;
  }

  // -- PRIVATE --

  @VisibleForTesting
  protected StatObjectResponse objectExists(String fullPath)
      throws ServerException,
          InsufficientDataException,
          ErrorResponseException,
          IOException,
          NoSuchAlgorithmException,
          InvalidKeyException,
          InvalidResponseException,
          XmlParserException,
          InternalException {
    return minioClient.statObject(
        StatObjectArgs.builder().bucket(bucket()).object(fullPath).build());
  }

  private Iterable<Result<Item>> listObjects(String prefix, boolean includeVersions) {
    return minioClient.listObjects(
        ListObjectsArgs.builder()
            .bucket(bucket())
            .prefix(prefix)
            .recursive(true)
            .includeVersions(includeVersions)
            .build());
  }

  @VisibleForTesting
  public int countObjects(String prefix) {
    Iterable<Result<Item>> results = listObjects(prefix, false);
    int count = 0;
    for (Result<Item> ignored : results) {
      count++;
    }
    return count;
  }

  /**
   * List all objects under {@code prefix}, collect them as {@link DeleteObject}, and batch-delete
   * them (1 000 per request — S3/MinIO limit).
   */
  private void deleteObjectsByPrefix(String prefix, boolean includeVersions) throws MinioException {
    Iterable<Result<Item>> objects = listObjects(prefix, includeVersions);

    // Mapper: with or without versionId depending on the use-case
    Function<Item, DeleteObject> toDeleteObject =
        includeVersions
            ? item -> new DeleteObject(item.objectName(), item.versionId())
            : item -> new DeleteObject(item.objectName());

    List<DeleteObject> batch = new ArrayList<>();
    int totalErrors = 0;
    for (Result<Item> result : objects) {
      try {
        batch.add(toDeleteObject.apply(result.get()));
      } catch (Exception e) {
        log.warn("Error listing object under prefix {}: {}", prefix, e.getMessage());
        continue;
      }

      if (batch.size() == BATCH_DELETE_SIZE) {
        totalErrors += flushDeletes(batch);
        batch.clear();
      }
    }

    if (!batch.isEmpty()) {
      totalErrors += flushDeletes(batch);
    }

    if (totalErrors > 0) {
      throw new MinioException(
          "Failed to delete " + totalErrors + " object(s) under prefix " + prefix);
    }
  }

  /**
   * Execute a batch delete and log any per-object errors.
   *
   * @return number of objects that failed to delete
   */
  private int flushDeletes(List<DeleteObject> objectsToDelete) {
    int errorCount = 0;
    Iterable<Result<DeleteError>> results =
        minioClient.removeObjects(
            RemoveObjectsArgs.builder().bucket(bucket()).objects(objectsToDelete).build());
    for (Result<DeleteError> result : results) {
      try {
        DeleteError error = result.get();
        log.error("Error deleting object {}; {}", error.objectName(), error.message());
        errorCount++;
      } catch (Exception e) {
        log.error("Error processing delete result", e);
        errorCount++;
      }
    }
    return errorCount;
  }

  private String bucket() {
    return minioConfig.getBucket();
  }

  /** Refuses a null or blank tenant: the key would sit outside every tenant's prefix. */
  private static void requireTenant(String tenantId) {
    Objects.requireNonNull(tenantId, "tenantId");
    if (tenantId.isBlank()) {
      throw new IllegalArgumentException("A storage object key needs a tenant");
    }
  }

  private String getPathForTenant(String tenantId, String objectName) {
    requireTenant(tenantId);
    if (objectName.startsWith("/")) {
      return tenantId + objectName;
    }
    return tenantId + "/" + objectName;
  }

  /**
   * Returns the platform-level path, prefixed with {@link #PLATFORM_PATH_PREFIX} to avoid
   * collisions with tenant-scoped paths (which use a UUID prefix).
   */
  private String getPlatformPath(String objectName) {
    if (objectName.startsWith("/")) {
      return PLATFORM_PATH_PREFIX + objectName.substring(1);
    }
    return PLATFORM_PATH_PREFIX + objectName;
  }
}
