package io.openaev.config;

import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the S3 object storage (RustFS, AWS S3, MinIO...).
 *
 * <p>This configuration class manages connection settings for the S3-compatible object storage used
 * to store documents, images, and other binary files. Properties are loaded from the application
 * configuration with the {@code openaev.s3.*} prefix. The former {@code minio.*} prefix is still
 * read as an alias (see {@link RenamedConfigurationSection}), and a warning lists the former names
 * at startup.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * openaev:
 *   s3:
 *     endpoint: localhost
 *     port: 9000
 *     access-key: minioadmin
 *     access-secret: minioadmin
 *     bucket: openaev
 *     secure: false
 * }</pre>
 *
 * @see io.openaev.service.FileService
 */
@Component
@Data
@Slf4j
public class MinioConfig {

  public static final String PREFIX = "openaev.s3";
  public static final String LEGACY_PREFIX = "minio";
  public static final List<String> KEYS =
      List.of("endpoint", "port", "bucket", "access-key", "access-secret", "secure");
  static final String USED_MESSAGE =
      "[S3] The minio configuration section is deprecated, rename it to openaev.s3: {}";
  static final String IGNORED_MESSAGE =
      "[S3] Former minio configuration ignored, the openaev.s3 name is set: {}";
  static final String SHADOWED_MESSAGE =
      "[S3] openaev.s3 configuration ignored, a minio name of higher priority is set: {}";

  /** The S3 server hostname or IP address. */
  private String endpoint;

  /** The access key (username) for S3 authentication. */
  private String accessKey;

  /** The secret key (password) for S3 authentication. */
  private String accessSecret;

  /** The port number for the S3 server. Default is 9000. */
  private int port;

  /** The default bucket name for storing files. Default is "openaev". */
  private String bucket;

  /** Whether to use HTTPS for S3 connections. Default is false. */
  private boolean secure;

  public MinioConfig(ConfigurableEnvironment environment) {
    RenamedConfigurationSection section =
        new RenamedConfigurationSection(environment, PREFIX, LEGACY_PREFIX);
    endpoint = section.get("endpoint", String.class, null);
    accessKey = section.get("access-key", String.class, null);
    accessSecret = section.get("access-secret", String.class, null);
    port = section.get("port", Integer.class, 9000);
    bucket = section.get("bucket", String.class, "openaev");
    secure = section.get("secure", Boolean.class, false);
    logDeprecations(section.deprecations(KEYS));
  }

  private static void logDeprecations(RenamedConfigurationSection.Deprecations deprecations) {
    if (!deprecations.used().isEmpty()) {
      log.warn(USED_MESSAGE, deprecations.used());
    }
    if (!deprecations.ignored().isEmpty()) {
      log.warn(IGNORED_MESSAGE, deprecations.ignored());
    }
    if (!deprecations.shadowed().isEmpty()) {
      log.warn(SHADOWED_MESSAGE, deprecations.shadowed());
    }
  }
}
