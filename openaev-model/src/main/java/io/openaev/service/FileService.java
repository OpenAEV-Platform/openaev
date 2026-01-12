package io.openaev.service;

import io.minio.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import io.openaev.config.MinioConfig;
import io.openaev.database.model.Document;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
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
 *
 * @see MinioConfig
 * @see FileContainer
 */
@Service
@Slf4j
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

  private MinioConfig minioConfig;
  private MinioClient minioClient;

  /**
   * Sets the MinIO configuration.
   *
   * @param minioConfig the MinIO configuration
   */
  @Autowired
  public void setMinioConfig(MinioConfig minioConfig) {
    this.minioConfig = minioConfig;
  }

  /**
   * Sets the MinIO client.
   *
   * @param minioClient the MinIO client instance
   */
  @Autowired
  public void setMinioClient(MinioClient minioClient) {
    this.minioClient = minioClient;
  }

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
    minioClient.putObject(
        PutObjectArgs.builder().bucket(minioConfig.getBucket()).object(name).stream(data, size, -1)
            .contentType(contentType)
            .build());
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
    String file = path + "/" + name;
    minioClient.putObject(
        PutObjectArgs.builder()
            .bucket(minioConfig.getBucket())
            .object(file)
            .userMetadata(Map.of("filename", name))
            .stream(data, data.available(), -1)
            .build());
    return file;
  }

  /**
   * Deletes a file from MinIO.
   *
   * @param name the file path/name to delete
   * @throws Exception if the deletion fails
   */
  public void deleteFile(String name) throws Exception {
    minioClient.removeObject(
        RemoveObjectArgs.builder().bucket(minioConfig.getBucket()).object(name).build());
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
    Iterable<Result<Item>> files =
        minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(minioConfig.getBucket())
                .recursive(true)
                .prefix(directory)
                .build());
    List<DeleteObject> deleteObjects = new ArrayList<>();
    files.forEach(
        itemResult -> {
          try {
            Item item = itemResult.get();
            deleteObjects.add(new DeleteObject(item.objectName()));
          } catch (Exception e) {
            // Dont care
          }
        });
    Iterable<Result<DeleteError>> removedObjects =
        minioClient.removeObjects(
            RemoveObjectsArgs.builder()
                .bucket(minioConfig.getBucket())
                .objects(deleteObjects)
                .build());
    for (Result<DeleteError> result : removedObjects) {
      try {
        DeleteError error = result.get();
        log.error("Error in deleting object {}; {}", error.objectName(), error.message());
      } catch (Exception e) {
        // Nothing to do
      }
    }
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
    try {
      GetObjectResponse objectStream =
          minioClient.getObject(
              GetObjectArgs.builder().bucket(minioConfig.getBucket()).object(name).build());
      InputStreamResource streamResource = new InputStreamResource(objectStream);
      return Optional.of(streamResource.getInputStream());
    } catch (Exception e) {
      log.error("Error during file access", e);
      return Optional.empty();
    }
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
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getInjectorImage(String injectType) {
    return getFilePath(INJECTORS_IMAGES_BASE_PATH + injectType + EXT_PNG);
  }

  /**
   * Retrieves a collector's image file.
   *
   * @param collectorId the collector identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCollectorImage(String collectorId) {
    return getFilePath(COLLECTORS_IMAGES_BASE_PATH + collectorId + EXT_PNG);
  }

  /**
   * Retrieves an executor's icon image file.
   *
   * @param executorId the executor identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getExecutorIconImage(String executorId) {
    return getFilePath(EXECUTORS_IMAGES_ICONS_BASE_PATH + executorId + EXT_PNG);
  }

  /**
   * Retrieves an executor's banner image file.
   *
   * @param executorId the executor identifier
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getExecutorBannerImage(String executorId) {
    return getFilePath(EXECUTORS_IMAGES_BANNERS_BASE_PATH + executorId + EXT_PNG);
  }

  /**
   * Retrieves a catalog connector's logo image file.
   *
   * @param fileName the logo filename
   * @return an Optional containing the image input stream, or empty if not found
   */
  public Optional<InputStream> getCatalogConnectorImage(String fileName) {
    return getFilePath(CONNECTORS_LOGO_PATH + fileName);
  }

  /**
   * Retrieves a file with its metadata as a FileContainer.
   *
   * @param fileTarget the target file path
   * @return an Optional containing the FileContainer with filename, content type, and stream
   */
  public Optional<FileContainer> getFileContainer(String fileTarget) {
    try {
      StatObjectResponse response =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(minioConfig.getBucket()).object(fileTarget).build());
      String filename = response.userMetadata().get("filename");
      Optional<InputStream> inputStream = getFilePath(fileTarget);
      FileContainer fileContainer =
          new FileContainer(filename, response.contentType(), inputStream.orElseThrow());
      return Optional.of(fileContainer);
    } catch (Exception e) {
      log.error("Error during file container access", e);
      return Optional.empty();
    }
  }
}
