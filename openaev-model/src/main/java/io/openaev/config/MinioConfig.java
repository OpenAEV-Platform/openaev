package io.openaev.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for MinIO object storage.
 *
 * <p>This configuration class manages connection settings for MinIO, an S3-compatible object
 * storage system used to store documents, images, and other binary files. Properties are loaded
 * from the application configuration with the {@code minio.*} prefix.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * minio:
 *   endpoint: localhost
 *   port: 9000
 *   access-key: minioadmin
 *   access-secret: minioadmin
 *   bucket: openaev
 *   secure: false
 * }</pre>
 *
 * @see io.openaev.service.FileService
 */
@Component
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioConfig {

  /** The MinIO server hostname or IP address. */
  @NotNull private String endpoint;

  /** The access key (username) for MinIO authentication. */
  @NotNull private String accessKey;

  /** The secret key (password) for MinIO authentication. */
  @NotNull private String accessSecret;

  /** The port number for the MinIO server. Default is 9000. */
  private int port = 9000;

  /** The default bucket name for storing files. Default is "openaev". */
  private String bucket = "openaev";

  /** Whether to use HTTPS for MinIO connections. Default is false. */
  private boolean secure = false;
}
