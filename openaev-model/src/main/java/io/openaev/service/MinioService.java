package io.openaev.service;

import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.context.TenantContext;
import io.openaev.multitenancy.DependenciesManager;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class MinioService implements DependenciesManager {

  private final MinioConfig minioConfig;
  private final MinioClient minioClient;

  /**
   * Create a bucket depending on the tenant uid
   *
   * @param uid
   * @throws Exception
   */
  @Override
  public void createDependencyForTenant(String uid) throws Exception {
    minioClient.makeBucket(
        MakeBucketArgs.builder().bucket(minioConfig.getBucket() + "-" + uid).build());
  }

  /**
   * Delete a bucket depending on the tenant uid
   *
   * @param uid
   * @throws Exception
   */
  @Override
  public void deleteDependencyForTenant(String uid) throws Exception {
    RemoveBucketArgs removeBucketArgs =
        RemoveBucketArgs.builder().bucket(minioConfig.getBucket() + "-" + uid).build();
    deleteAllObjectsAndVersionsFromTenantBucket(minioConfig.getBucket() + "-" + uid);
    minioClient.removeBucket(removeBucketArgs);
  }

  public void uploadFileInTenantBucket(String name, InputStream data, long size, String contentType)
      throws Exception {
    minioClient.putObject(
        PutObjectArgs.builder().bucket(getCurrentBucket()).object(name).stream(data, size, -1)
            .contentType(contentType)
            .build());
  }

  public void uploadStreamInTenantBucket(String file, String name, InputStream data)
      throws Exception {
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(getCurrentBucket())
            .object(file)
            .userMetadata(Map.of("filename", name))
            .stream(data, data.available(), -1)
            .build());
  }

  public void deleteFileInTenantBucket(String name) throws Exception {
    minioClient.removeObject(
        RemoveObjectArgs.builder().bucket(getCurrentBucket()).object(name).build());
  }

  public void deleteDirectoryInTenantBucket(String directory) {
    Iterable<Result<Item>> files =
        minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(getCurrentBucket())
                .recursive(true)
                .prefix(directory)
                .build());
    List<DeleteObject> deleteObjects = new ArrayList<>();
    files.forEach(
        itemResult -> {
          try {
            Item item = itemResult.get();
            deleteObjects.add(new DeleteObject(item.objectName()));
          } catch (Exception e) {
            // Dont care
          }
        });
    Iterable<Result<DeleteError>> removedObjects =
        minioClient.removeObjects(
            RemoveObjectsArgs.builder().bucket(getCurrentBucket()).objects(deleteObjects).build());
    for (Result<DeleteError> result : removedObjects) {
      try {
        DeleteError error = result.get();
        log.error("Error in deleting object {}; {}", error.objectName(), error.message());
      } catch (Exception e) {
        // Nothing to do
      }
    }
  }

  public Optional<InputStream> getFilePathInTenant(String name) {
    try {
      GetObjectResponse objectStream =
          minioClient.getObject(
              GetObjectArgs.builder().bucket(getCurrentBucket()).object(name).build());
      InputStreamResource streamResource = new InputStreamResource(objectStream);
      return Optional.of(streamResource.getInputStream());
    } catch (Exception e) {
      log.error("Error during file access", e);
      return Optional.empty();
    }
  }

  public Optional<FileContainer> getFileContainerInTenant(String fileTarget) {
    try {
      StatObjectResponse response =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(getCurrentBucket()).object(fileTarget).build());
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

  private void deleteAllObjectsAndVersionsFromTenantBucket(String bucket) throws Exception {
    List<DeleteObject> objectsToDelete = new ArrayList<>();
    Iterable<Result<Item>> objects =
        minioClient.listObjects(
            ListObjectsArgs.builder().bucket(bucket).recursive(true).includeVersions(true).build());

    for (Result<Item> result : objects) {
      Item item = result.get();
      String objectName = item.objectName();
      String versionId = item.versionId(); // null if versioning not enabled

      objectsToDelete.add(new DeleteObject(objectName, versionId));

      // Batch delete every 1000 objects (MinIO/S3 limit per request)
      if (objectsToDelete.size() == 1000) {
        minioClient.removeObjects(
            RemoveObjectsArgs.builder().bucket(bucket).objects(objectsToDelete).build());
        objectsToDelete.clear();
      }
    }

    // Flush remaining objects
    if (!objectsToDelete.isEmpty()) {
      minioClient.removeObjects(
          RemoveObjectsArgs.builder().bucket(bucket).objects(objectsToDelete).build());
    }
  }

  private String getCurrentBucket() {
    return minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant();
  }
}
