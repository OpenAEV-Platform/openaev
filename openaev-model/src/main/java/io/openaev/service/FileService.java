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
   * Uploads a file from an input stream under the path of the named tenant, the tenant of the row
   * the object belongs to, so a later read of that row resolves the object.
   *
   * @param tenantId the tenant whose path the object is written under
   * @param name the target file path/name in the bucket
   * @param data the input stream containing the file data
   * @param size the size of the file in bytes
   * @param contentType the MIME type of the file
   * @throws Exception if the upload fails
   */
  public void uploadFile(
      String tenantId, String name, InputStream data, long size, String contentType)
      throws Exception {
    minioService.uploadFileForTenant(tenantId, name, data, size, contentType);
  }

  /**
   * Uploads a multipart file under an explicitly named tenant path.
   *
   * @param tenantId the tenant whose path the object is written under
   * @param name the target file path/name in the bucket
   * @param file the multipart file from an HTTP request
   * @throws Exception if the upload fails
   */
  public void uploadFile(String tenantId, String name, MultipartFile file) throws Exception {
    uploadFile(tenantId, name, file.getInputStream(), file.getSize(), file.getContentType());
  }

  /**
   * Uploads a stream with its filename as metadata, under the path of the named tenant, the tenant
   * of the row the object belongs to.
   *
   * @param tenantId the tenant whose path the object is written under
   * @param path the directory path within the bucket
   * @param name the filename
   * @param data the input stream containing the file data
   * @return the full path of the uploaded file, relative to the tenant's own prefix
   * @throws Exception if the upload fails
   */
  public String uploadStream(String tenantId, String path, String name, InputStream data)
      throws Exception {
    String file = path.endsWith("/") ? path + name : path + "/" + name;
    minioService.uploadStreamForTenant(tenantId, file, name, data);
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
   * Deletes a document's stored object under the tenant that owns it, the delete-side counterpart
   * of {@link #getFile(Document, String)}: the caller passes the tenant of the row being deleted.
   *
   * @param tenantId the owning tenant
   * @param name the object name/path to delete
   * @throws Exception if the deletion fails
   */
  public void deleteFile(String tenantId, String name) throws Exception {
    minioService.deleteFileForTenant(tenantId, name);
  }

  /**
   * Deletes all files in a directory recursively, under the path of the tenant that owns them.
   *
   * @param tenantId the tenant whose path the directory is removed from
   * @param directory the directory prefix to delete
   */
  public void deleteDirectory(String tenantId, String directory) {
    minioService.deleteDirectoryForTenant(tenantId, directory);
  }

  /**
   * Retrieves a document file from MinIO, but only when the document belongs to the given owning
   * tenant.
   *
   * <p>The caller passes the tenant of the parent it serves the document through (the exercise or
   * scenario of a player download, the inject of an attachment, the exported parent, the reporting
   * generation, or the document's own tenant for an id-addressed read that already checked the
   * request scope). A document whose tenant differs from that owning tenant was reached through a
   * parent in another tenant, so its bytes are not served: the result is empty, the same as a
   * missing object.
   *
   * @param document the document entity containing the file target path
   * @param owningTenantId the tenant the document is served under; the object is returned only if
   *     the document belongs to it
   * @return an Optional containing the file input stream, or empty if not found or out of tenant
   */
  public Optional<InputStream> getFile(Document document, String owningTenantId) {
    String documentTenantId = document.getTenant().getId();
    if (!documentTenantId.equals(owningTenantId)) {
      return Optional.empty();
    }
    return minioService.getFilePathForTenant(documentTenantId, document.getTarget());
  }

  /**
   * Serves a connector's image, from the requesting tenant's path first, then from the default
   * tenant's path (see {@link #getPlatformImage}).
   *
   * @param tenantId the tenant the request reads in
   * @param connectorType the kind of connector
   * @param type the connector type
   * @return the image, or 404 when neither path holds it
   */
  public ResponseEntity<InputStreamResource> getConnectorImage(
      String tenantId, ConnectorType connectorType, String type) {
    String filePath;
    switch (connectorType) {
      case ConnectorType.COLLECTOR -> filePath = COLLECTORS_IMAGES_BASE_PATH;
      case ConnectorType.INJECTOR -> filePath = INJECTORS_IMAGES_BASE_PATH;
      case ConnectorType.EXECUTOR -> filePath = EXECUTORS_IMAGES_ICONS_BASE_PATH;
      case ConnectorType.SECRETS_PROVIDER -> filePath = SECRETS_PROVIDERS_IMAGES_ICONS_BASE_PATH;
      default -> throw new IllegalArgumentException("Unsupported connector type: " + connectorType);
    }

    Optional<InputStream> image = this.getPlatformImage(tenantId, filePath + type + EXT_PNG);
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
   * @param tenantId the tenant the request reads in
   * @param collectorId the collector identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCollectorImage(String tenantId, String collectorId) {
    return getPlatformImage(tenantId, COLLECTORS_IMAGES_BASE_PATH + collectorId + EXT_PNG);
  }

  /**
   * Retrieves an executor's banner image file.
   *
   * @param tenantId the tenant the request reads in
   * @param executorId the executor identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getExecutorBannerImage(String tenantId, String executorId) {
    return getPlatformImage(tenantId, EXECUTORS_IMAGES_BANNERS_BASE_PATH + executorId + EXT_PNG);
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
   * Retrieves a connector image, from the given tenant's path first (an integration registered in
   * that tenant), then from the default tenant's path (built-in assets uploaded at startup).
   *
   * @param tenantId the tenant the request reads in
   * @param filePath the file path to retrieve
   * @return an Optional containing the image input stream, or empty if not found
   */
  private Optional<InputStream> getPlatformImage(String tenantId, String filePath) {
    Optional<InputStream> tenantFile = minioService.getFilePathForTenant(tenantId, filePath);
    if (tenantFile.isPresent() || Tenant.DEFAULT_TENANT_UUID.equals(tenantId)) {
      return tenantFile;
    }
    return minioService.getFilePathForTenant(Tenant.DEFAULT_TENANT_UUID, filePath);
  }

  /**
   * Retrieves a file with its metadata as a FileContainer, under the path of the tenant that owns
   * it.
   *
   * @param tenantId the tenant whose path the object is read from
   * @param fileTarget the target file path
   * @return an Optional containing the FileContainer with filename, content type, and stream
   */
  public Optional<FileContainer> getFileContainer(String tenantId, String fileTarget) {
    return minioService.getFileContainerForTenant(tenantId, fileTarget);
  }

  // -- PLATFORM FILES: shared by every tenant --

  public void uploadInPlatform(String path, InputStream data) throws Exception {
    minioService.uploadStreamInPlatformPath(path, path.substring(path.lastIndexOf('/') + 1), data);
  }

  public Optional<InputStream> getInPlatform(String path) {
    return minioService.getFilePathInPlatform(path);
  }

  public boolean existsInPlatform(String path) {
    return minioService.existsInPlatform(path);
  }
}
