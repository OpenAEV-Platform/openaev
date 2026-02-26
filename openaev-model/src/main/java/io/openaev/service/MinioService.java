package io.openaev.service;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.openaev.config.MinioConfig;
import io.openaev.multitenancy.DependenciesManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Service to create and delete minIO buckets */
@Service
public class MinioService implements DependenciesManager {

  private MinioConfig minioConfig;
  private MinioClient minioClient;

  /**
   * Sets the MinIO configuration.
   *
   * @param minioConfig
   */
  @Autowired
  public void setMinioConfig(MinioConfig minioConfig) {
    this.minioConfig = minioConfig;
  }

  /**
   * Sets the MinIO client.
   *
   * @param minioClient
   */
  @Autowired
  public void setMinioClient(MinioClient minioClient) {
    this.minioClient = minioClient;
  }

  /**
   * Create a bucket depending on the tenant uid
   *
   * @param uid
   * @throws Exception
   */
  @Override
  public void createDependency(String uid) throws Exception {
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
  public void deleteDependency(String uid) throws Exception {
    RemoveBucketArgs removeBucketArgs =
        RemoveBucketArgs.builder().bucket(minioConfig.getBucket() + "-" + uid).build();
    minioClient.removeBucket(removeBucketArgs);
  }
}
