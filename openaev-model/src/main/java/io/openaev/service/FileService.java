package io.openaev.service;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tenant;
import java.io.InputStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    String file = path.endsWith("/") ? path + name :  path + "/" + name;
    minioService.uploadStreamInTenantPath(file, name, data);
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

  /**
   * Retrieves an injector's image file.
   *
   * @param injectType the injector type identifier
   * @param isExternal indicates if the file is a built-in asset (false) or a tenant-specific file (true)
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getInjectorImage(String injectType, boolean isExternal) {
    return getPlatformImage(INJECTORS_IMAGES_BASE_PATH + injectType + EXT_PNG, isExternal);
  }

  /**
   * Retrieves a collector's image file.
   *
   * @param collectorId the collector identifier
   * @param isExternal indicates if the file is a built-in asset (false) or a tenant-specific file (true)
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCollectorImage(String collectorId, boolean isExternal) {
    return getPlatformImage(COLLECTORS_IMAGES_BASE_PATH + collectorId + EXT_PNG, isExternal);
  }

  /**
   * Retrieves an executor's icon image file.
   *
   * @param executorId the executor identifier
   * @param isExternal indicates if the file is a built-in asset (false) or a tenant-specific file (true)
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getExecutorIconImage(String executorId, boolean isExternal) {
    return getPlatformImage(EXECUTORS_IMAGES_ICONS_BASE_PATH + executorId + EXT_PNG, isExternal);
  }

  /**
   * Retrieves an executor's banner image file.
   *
   * @param executorId the executor identifier
   * @param isExternal indicates if the file is a built-in asset (false) or a tenant-specific file (true)
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getExecutorBannerImage(String executorId, boolean isExternal) {
    return getPlatformImage(EXECUTORS_IMAGES_BANNERS_BASE_PATH + executorId + EXT_PNG, isExternal);
  }

  /**
   * Retrieves a catalog connector's logo image file.
   *
   * @param fileName the logo filename
   * @param isExternal indicates if the file is a built-in asset (false) or a tenant-specific file (true)
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCatalogConnectorImage(String fileName, boolean isExternal) {
    return getPlatformImage(CONNECTORS_LOGO_PATH + fileName, isExternal);
  }

  /**
   * Platform assets are written once under the default tenant during startup for built in assets
   * should fall back to specific tenant if isExternal is true.
   *
   * @param filePath to retrieve
   * @param isExternal indicates if the file is a built-in asset (false) or from an external asset (true)
   * @return finded file
   */
  private Optional<InputStream> getPlatformImage(String filePath, boolean isExternal) {
    Optional<InputStream> tenantFile = getFilePath(filePath);
    if (tenantFile.isPresent()) {
      return tenantFile;
    }
    return minioService.getFilePathForTenant(isExternal ? TenantContext.getCurrentTenant() : Tenant.DEFAULT_TENANT_UUID, filePath);
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
