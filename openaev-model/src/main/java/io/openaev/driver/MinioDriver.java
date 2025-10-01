package io.openaev.driver;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.credentials.*;
import io.openaev.config.MinioConfig;
import io.openaev.config.S3Config;
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
    if (!found) {
      minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
    }
    return minioClient;
  }
}
