package io.openaev.service;

import com.google.common.annotations.VisibleForTesting;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.context.TenantContext;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.multitenancy.MinioException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

  private static final int BATCH_DELETE_SIZE = 1000;

  private final MinioConfig minioConfig;
  private final MinioClient minioClient;

  @Override
  public void createDependencyForTenant(String tenantId) {
    log.info("Tenant {} created — files will be stored under path prefix {}/", tenantId, tenantId);
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

  public String uploadFileInTenantPath(
      String fileName, InputStream data, long size, String contentType) throws Exception {
    minioClient.putObject(
        PutObjectArgs.builder().bucket(bucket()).object(getTenantPath(fileName)).stream(
                data, size, -1)
            .contentType(contentType)
            .build());
    return getTenantPath(fileName);
  }

  public String uploadStreamInTenantPath(String fileName, String name, InputStream data)
      throws Exception {
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(bucket())
            .object(getTenantPath(fileName))
            .userMetadata(Map.of("filename", name))
            .stream(data, data.available(), -1)
            .build());
    return getTenantPath(fileName);
  }

  // -- READ --

  public Optional<InputStream> getFilePathInTenant(String name) {
    try {
      GetObjectResponse objectStream =
          minioClient.getObject(
              GetObjectArgs.builder().bucket(bucket()).object(getTenantPath(name)).build());
      InputStreamResource streamResource = new InputStreamResource(objectStream);
      return Optional.of(streamResource.getInputStream());
    } catch (Exception e) {
      log.error("Error during file access", e);
      return Optional.empty();
    }
  }

  public Optional<FileContainer> getFileContainerInTenant(String fileTarget) {
    try {
      StatObjectResponse response = objectExists(getTenantPath(fileTarget));
      String filename = response.userMetadata().get("filename");
      Optional<InputStream> inputStream = getFilePathInTenant(fileTarget);
      FileContainer fileContainer =
          new FileContainer(filename, response.contentType(), inputStream.orElseThrow());
      return Optional.of(fileContainer);
    } catch (Exception e) {
      log.error("Error during file container access", e);
      return Optional.empty();
    }
  }

  // -- DELETE --

  public void deleteFileInTenantPath(String name) throws Exception {
    minioClient.removeObject(
        RemoveObjectArgs.builder().bucket(bucket()).object(getTenantPath(name)).build());
  }

  public void deleteDirectoryInTenantPath(String directory) {
    try {
      deleteObjectsByPrefix(getTenantPath(directory), false);
    } catch (Exception e) {
      log.error("Error deleting directory {} for tenant", directory, e);
    }
  }

  // -- HELPERS --

  public void isTenantPathExists() throws Exception {
    minioClient.statObject(
        StatObjectArgs.builder().bucket(bucket()).object(getTenantPath("")).build());
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
  public int countObjectsForCurrentTenant(String prefix) {
    Iterable<Result<Item>> results = listObjects(getTenantPath(prefix), false);
    int count = 0;
    for (Result<Item> ignored : results) {
      count++;
    }
    return count;
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

  /** Returns the tenant-prefixed path for the given object name. */
  private String getTenantPath(String objectName) {
    String tenantId = TenantContext.getCurrentTenant();
    if (objectName.startsWith("/")) {
      return tenantId + objectName;
    }
    return tenantId + "/" + objectName;
  }
}
