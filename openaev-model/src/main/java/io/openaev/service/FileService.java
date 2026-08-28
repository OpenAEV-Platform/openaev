package io.openaev.service;

import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tenant;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for file storage operations using MinIO (S3-compatible object storage).
 *
 * <p>This service handles all file operations including:
 *
 * <ul>
 *   <li>Uploading files and streams
 *   <li>Downloading files
 *   <li>Deleting files and directories
 *   <li>Retrieving images for injectors, collectors, executors, and connectors
 * </ul>
 *
 * <p>Files are organized in predefined directory structures within the MinIO bucket.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileService {

  /** Base path for injector images. */
  public static final String INJECTORS_IMAGES_BASE_PATH = "/injectors/images/";

  /** Base path for collector images. */
  public static final String COLLECTORS_IMAGES_BASE_PATH = "/collectors/images/";

  /** Base path for executor icon images. */
  public static final String EXECUTORS_IMAGES_ICONS_BASE_PATH = "/executors/images/icons/";

  /** Base path for secret_provider icon images. */
  public static final String SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH =
      "/secrets-providers/images/icons/";

  /** Base path for executor banner images. */
  public static final String EXECUTORS_IMAGES_BANNERS_BASE_PATH = "/executors/images/banners/";

  /** Base path for connector logo images. */
  public static final String CONNECTORS_LOGO_PATH = "/connectors/logos/";

  /** PNG file extension. */
  public static final String EXT_PNG = ".png";

  private final MinioService minioService;

  /**
   * Uploads a file from an input stream to MinIO.
   *
   * @param name the target file path/name in the bucket
   * @param data the input stream containing the file data
   * @param size the size of the file in bytes
   * @param contentType the MIME type of the file
   * @throws Exception if the upload fails
   */
  public void uploadFile(String name, InputStream data, long size, String contentType)
      throws Exception {
    minioService.uploadFileInTenantPath(name, data, size, contentType);
  }

  /**
   * Uploads a stream to MinIO with metadata.
   *
   * @param path the directory path within the bucket
   * @param name the filename
   * @param data the input stream containing the file data
   * @return the full path of the uploaded file
   * @throws Exception if the upload fails
   */
  public String uploadStream(String path, String name, InputStream data) throws Exception {
    String file = path.endsWith("/") ? path + name : path + "/" + name;
    minioService.uploadStreamInTenantPath(file, name, data);
    return file;
  }

  /**
   * Uploads a catalog asset (e.g. connector logo) to the platform-level MinIO path, shared across
   * all tenants. Use this instead of {@link #uploadStream} for catalog logos written by {@code
   * insertCatalogEntry()} or the catalog ingestion service.
   *
   * @param path the directory path within the platform space
   * @param name the filename
   * @param data the input stream containing the file data
   * @return the object key within the platform namespace (without the "platform/" prefix)
   * @throws Exception if the upload fails
   */
  public String uploadCatalogLogo(String path, String name, InputStream data) throws Exception {
    String file = path.endsWith("/") ? path + name : path + "/" + name;
    minioService.uploadStreamInPlatformPath(file, name, data);
    return file;
  }

  /**
   * Deletes a file from MinIO.
   *
   * @param name the file path/name to delete
   * @throws Exception if the deletion fails
   */
  public void deleteFile(String name) throws Exception {
    minioService.deleteFileInTenantPath(name);
  }

  /**
   * Deletes all files in a directory recursively.
   *
   * <p>This method lists all objects with the given directory prefix and deletes them. Errors
   * during individual deletions are logged but do not stop the operation.
   *
   * @param directory the directory prefix to delete
   */
  public void deleteDirectory(String directory) {
    minioService.deleteDirectoryInTenantPath(directory);
  }

  /**
   * Uploads a multipart file to MinIO.
   *
   * @param name the target file path/name in the bucket
   * @param file the multipart file from an HTTP request
   * @throws Exception if the upload fails
   */
  public void uploadFile(String name, MultipartFile file) throws Exception {
    uploadFile(name, file.getInputStream(), file.getSize(), file.getContentType());
  }

  /**
   * Retrieves a file from MinIO as an input stream.
   *
   * @param name the file path/name to retrieve
   * @return an Optional containing the input stream, or empty if the file doesn't exist or an error
   *     occurs
   */
  private Optional<InputStream> getFilePath(String name) {
    return minioService.getFilePathInTenant(name);
  }

  /**
   * Retrieves a document file from MinIO.
   *
   * @param document the document entity containing the file target path
   * @return an Optional containing the file input stream, or empty if not found
   */
  public Optional<InputStream> getFile(Document document) {
    return getFilePath(document.getTarget());
  }

  public ResponseEntity<InputStreamResource> getConnectorImage(
      ConnectorType connectorType, String type) {
    String filePath;
    switch (connectorType) {
      case ConnectorType.COLLECTOR -> filePath = COLLECTORS_IMAGES_BASE_PATH;
      case ConnectorType.INJECTOR -> filePath = INJECTORS_IMAGES_BASE_PATH;
      case ConnectorType.EXECUTOR -> filePath = EXECUTORS_IMAGES_ICONS_BASE_PATH;
      case ConnectorType.SECRETS_PROVIDER -> filePath = SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH;
      default -> throw new IllegalArgumentException("Unsupported connector type: " + connectorType);
    }

    Optional<InputStream> image = this.getPlatformImage(filePath + type + EXT_PNG);
    if (image.isEmpty()) {
      return ResponseEntity.notFound().build();
    }

    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
          .body(new InputStreamResource(image.get()));
    } catch (Exception e) {
      log.warn("Unable to read connector image {}", type, e);
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Retrieves a collector's image file.
   *
   * @param collectorId the collector identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCollectorImage(String collectorId) {
    return getPlatformImage(COLLECTORS_IMAGES_BASE_PATH + collectorId + EXT_PNG);
  }

  /**
   * Retrieves an executor's banner image file.
   *
   * @param executorId the executor identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getExecutorBannerImage(String executorId) {
    return getPlatformImage(EXECUTORS_IMAGES_BANNERS_BASE_PATH + executorId + EXT_PNG);
  }

  /**
   * Retrieves a catalog connector's logo image file from the platform-level MinIO path.
   *
   * <p>Falls back to the legacy tenant-scoped paths for instances deployed before the migration to
   * platform-level storage:
   *
   * <ul>
   *   <li>{@code platform/connectors/logos/{fileName}} — current path (bucket root)
   *   <li>{@code {DEFAULT_TENANT_UUID}/platform/connectors/logos/{fileName}} — legacy path
   * </ul>
   *
   * @param fileName the logo filename
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCatalogConnectorImage(String fileName) {
    Optional<InputStream> platform =
        minioService.getFilePathInPlatform(CONNECTORS_LOGO_PATH + fileName);
    if (platform.isPresent()) {
      return platform;
    }
    // Fallback: legacy paths before platform storage migration
    Optional<InputStream> legacyDefaultTenant =
        minioService.getFilePathForTenant(
            Tenant.DEFAULT_TENANT_UUID, CONNECTORS_LOGO_PATH + fileName);
    if (legacyDefaultTenant.isPresent()) {
      return legacyDefaultTenant;
    }
    return minioService.getFilePathForTenant(
        Tenant.DEFAULT_TENANT_UUID, "platform" + CONNECTORS_LOGO_PATH + fileName);
  }

  /**
   * Retrieves a platform image, checking the current tenant path first (for externally registered
   * integrations) then falling back to the default tenant (for built-in platform assets uploaded at
   * startup).
   *
   * @param filePath the file path to retrieve
   * @return an Optional containing the image input stream, or empty if not found
   */
  private Optional<InputStream> getPlatformImage(String filePath) {
    Optional<InputStream> tenantFile = getFilePath(filePath);
    if (tenantFile.isPresent()) {
      return tenantFile;
    }
    return minioService.getFilePathForTenant(Tenant.DEFAULT_TENANT_UUID, filePath);
  }

  /**
   * Retrieves a file with its metadata as a FileContainer.
   *
   * @param fileTarget the target file path
   * @return an Optional containing the FileContainer with filename, content type, and stream
   */
  public Optional<FileContainer> getFileContainer(String fileTarget) {
    return minioService.getFileContainerInTenant(fileTarget);
  }
}
