package io.openaev.driver;

import io.minio.*;
import io.minio.credentials.*;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.config.S3Config;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.minio.CopySource;
import io.openaev.service.MinioService;
import java.util.Set;
import java.util.stream.Collectors;
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

  private final TenantRepository tenantRepository;

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

  // OkHttp socket-level timeouts (connect / write / read). These are idle timeouts, not
  // whole-request deadlines, so large uploads/downloads still work as long as bytes flow.
  // Without them the client waits forever on an unresponsive endpoint; callers running inside
  // a database transaction then pin their connection (and its row locks) indefinitely.
  private static final long CONNECT_TIMEOUT_MS = 10_000L;
  private static final long WRITE_TIMEOUT_MS = 60_000L;
  private static final long READ_TIMEOUT_MS = 60_000L;

  @Bean
  public MinioClient minioClient() throws Exception {
    MinioClient minioClient = getMinioClient();
    minioClient.setTimeout(CONNECT_TIMEOUT_MS, WRITE_TIMEOUT_MS, READ_TIMEOUT_MS);
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
    Set<String> tenants =
        tenantRepository.findAllIdsByDeletedAtIsNull().stream()
            .map(id -> id + "/")
            .collect(Collectors.toSet());

    Iterable<Result<Item>> objects =
        minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).recursive(true).build());

    for (Result<Item> result : objects) {
      Item item = result.get();
      String objectName = item.objectName();
      // Object names can carry a leading '/': normalize once so the tenant and platform prefix
      // checks below see the same shape the new object name is built from.
      String normalizedName = objectName.startsWith("/") ? objectName.substring(1) : objectName;

      // Skip files already under a tenant path
      if (tenants.stream().anyMatch(normalizedName::startsWith)) {
        continue;
      }

      // Skip platform-scoped shared assets: they intentionally live under the root-level
      // "platform/" prefix (no tenant) and are re-uploaded there by the integration factories
      // at startup. Moving them under the default tenant made every boot re-migrate the same
      // connector logos forever (migrate -> re-upload -> migrate again on next boot).
      if (normalizedName.startsWith(MinioService.PLATFORM_PATH_PREFIX)) {
        continue;
      }

      String newObjectName = Tenant.DEFAULT_TENANT_UUID + "/" + normalizedName;

      log.info("Migrating file '{}' to '{}'", objectName, newObjectName);

      // Copy to new path under default tenant

      minioClient.copyObject(
          CopyObjectArgs.builder()
              .bucket(bucket)
              .object(newObjectName)
              .source(CopySource.customBuilder().bucket(bucket).object(objectName).build())
              .build());

      // Remove original
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }
  }
}
