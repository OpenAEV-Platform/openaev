package io.openaev.driver;

import io.minio.*;
import io.minio.credentials.*;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.config.S3Config;
import io.openaev.database.model.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioDriver {
  private final MinioConfig minioConfig;
  private final S3Config s3Config;

  /** Create the Minio Client */
  public MinioClient getMinioClient() {
    MinioClient minioClient;
    if (s3Config.isUseAwsRole()) {
      String stsEndpoint = null;
      if (s3Config.getStsEndpoint() != null && !s3Config.getStsEndpoint().isEmpty()) {
        stsEndpoint = s3Config.getStsEndpoint();
      }
      IamAwsProvider provider = new IamAwsProvider(stsEndpoint, null);

      minioClient =
          MinioClient.builder()
              .endpoint(minioConfig.getEndpoint())
              .credentialsProvider(provider)
              .build();
    } else {
      minioClient =
          MinioClient.builder()
              .endpoint(minioConfig.getEndpoint(), minioConfig.getPort(), minioConfig.isSecure())
              .credentials(minioConfig.getAccessKey(), minioConfig.getAccessSecret())
              .build();
    }
    return minioClient;
  }

  @Bean
  public MinioClient minioClient() throws Exception {
    MinioClient minioClient = getMinioClient();
    String bucket = minioConfig.getBucket();

    // Make bucket if not exist.
    BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucket).build();
    boolean found = minioClient.bucketExists(bucketExistsArgs);
    if (!found) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    } else {
      // Migrate existing root-level files to default tenant path
      moveDefaultTenantFiles(minioClient, bucket);
    }
    return minioClient;
  }

  /**
   * Migrates existing root-level files (not already under a tenant path) into the default tenant
   * path prefix. This ensures backward compatibility when switching from flat storage to path-based
   * tenant isolation.
   */
  private void moveDefaultTenantFiles(MinioClient minioClient, String bucket) throws Exception {
    String defaultTenantPrefix = Tenant.DEFAULT_TENANT_UUID + "/";

    Iterable<Result<Item>> objects =
        minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build());

    for (Result<Item> result : objects) {
      Item item = result.get();
      String objectName = item.objectName();

      // Skip files already under a tenant path
      if (objectName.startsWith(defaultTenantPrefix)) {
        continue;
      }

      String newObjectName = defaultTenantPrefix + objectName;

      log.info("Migrating file '{}' to '{}'", objectName, newObjectName);

      // Copy to new path under default tenant
      minioClient.copyObject(
          CopyObjectArgs.builder()
              .bucket(bucket)
              .object(newObjectName)
              .source(CopySource.builder().bucket(bucket).object(objectName).build())
              .build());

      // Remove original
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }
  }
}
