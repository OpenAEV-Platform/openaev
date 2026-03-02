package io.openaev.driver;

import io.minio.*;
import io.minio.credentials.*;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.config.S3Config;
import io.openaev.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinioDriver {
  private final MinioConfig minioConfig;
  private final S3Config s3Config;

  /**
   * Create the Minio Client
   *
   * @return
   * @throws Exception
   */
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
    // Make bucket if not exist.
    BucketExistsArgs bucketExistsArgs =
        BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build();
    boolean found = minioClient.bucketExists(bucketExistsArgs);
    if (found && bucketExistsArgs.bucket().equals(minioConfig.getBucket())) {
      moveDefaultTenantFiles(minioClient, bucketExistsArgs);
    }
    BucketExistsArgs defaultTenantBucketExistsArgs =
        BucketExistsArgs.builder()
            .bucket(minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant())
            .build();
    boolean defaultTenantBucket = minioClient.bucketExists(defaultTenantBucketExistsArgs);
    if (!defaultTenantBucket) {
      minioClient.makeBucket(
          MakeBucketArgs.builder()
              .bucket(minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant())
              .build());
    }
    return minioClient;
  }

  /**
   * Moves the default tenant files to another one with the correct name
   *
   * @param minioClient
   * @param bucketExistsArgs
   * @throws Exception
   */
  private void moveDefaultTenantFiles(MinioClient minioClient, BucketExistsArgs bucketExistsArgs)
      throws Exception {

    minioClient.makeBucket(
        MakeBucketArgs.builder()
            .bucket(minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant())
            .build());
    if (!minioClient.bucketExists(
        BucketExistsArgs.builder()
            .bucket(minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant())
            .build())) {
      minioClient.makeBucket(
          MakeBucketArgs.builder()
              .bucket(minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant())
              .build());
    }
    Iterable<Result<Item>> objects =
        minioClient.listObjects(
            ListObjectsArgs.builder().bucket(bucketExistsArgs.bucket()).recursive(true).build());

    for (Result<Item> result : objects) {
      Item item = result.get();
      String objectName = item.objectName();

      minioClient.copyObject(
          CopyObjectArgs.builder()
              .bucket(minioConfig.getBucket() + "-" + TenantContext.getCurrentTenant())
              .object(objectName)
              .source(
                  CopySource.builder().bucket(bucketExistsArgs.bucket()).object(objectName).build())
              .build());
    }

    for (Result<Item> result :
        minioClient.listObjects(
            ListObjectsArgs.builder().bucket(bucketExistsArgs.bucket()).recursive(true).build())) {

      Item item = result.get();
      minioClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(bucketExistsArgs.bucket())
              .object(item.objectName())
              .build());
    }

    minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketExistsArgs.bucket()).build());
  }
}
