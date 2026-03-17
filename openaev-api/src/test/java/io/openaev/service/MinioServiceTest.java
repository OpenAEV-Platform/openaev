package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.minio.errors.ErrorResponseException;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinioServiceTest extends IntegrationTest {

  @Autowired private MinioService minioService;

  private static final String TEST_FILE_NAME = "documents/test-report.pdf";
  private static final String TEST_CONTENT_TYPE = "application/pdf";
  private static final byte[] TEST_CONTENT = "test-file-content".getBytes(StandardCharsets.UTF_8);

  // -- CREATE/UPDATE --

  @Test
  void should_upload_file_in_tenant_path() throws Exception {
    // -- ACT --
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    String fullPath =
        minioService.uploadFileInTenantPath(
            TEST_FILE_NAME, data, TEST_CONTENT.length, TEST_CONTENT_TYPE);

    // -- ASSERT --
    assertDoesNotThrow(() -> minioService.objectExists(fullPath));
  }

  @Test
  void should_upload_stream_with_filename_metadata() throws Exception {
    // -- ARRANGE --
    String file = "streams/stream-uuid-001";
    String originalName = "quarterly-report.xlsx";
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);

    // -- ACT --
    String fullPath = minioService.uploadStreamInTenantPath(file, originalName, data);

    // -- ASSERT --
    assertDoesNotThrow(() -> minioService.objectExists(fullPath));
  }

  // -- READ --

  @Test
  void should_get_file_in_tenant_path() throws Exception {
    // -- ARRANGE --
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    minioService.uploadFileInTenantPath(
        "readable/test-read.txt", data, TEST_CONTENT.length, TEST_CONTENT_TYPE);

    // -- ACT --
    Optional<InputStream> result = minioService.getFilePathInTenant("readable/test-read.txt");

    // -- ASSERT --
    assertThat(result).isPresent();
    byte[] readBytes = result.get().readAllBytes();
    assertThat(readBytes).isEqualTo(TEST_CONTENT);
  }

  @Test
  void should_return_empty_when_file_does_not_exist() {
    // -- ACT --
    Optional<InputStream> result = minioService.getFilePathInTenant("nonexistent/ghost.bin");

    // -- ASSERT --
    assertThat(result).isEmpty();
  }

  @Test
  void should_get_file_container_with_metadata() throws Exception {
    // -- ARRANGE --
    String file = "containers/doc-uuid-42";
    String originalName = "report.docx";
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    minioService.uploadStreamInTenantPath(file, originalName, data);

    // -- ACT --
    Optional<FileContainer> result = minioService.getFileContainerInTenant(file);

    // -- ASSERT --
    assertThat(result).isPresent();
    FileContainer container = result.get();
    assertThat(container.getName()).isEqualTo(originalName);
    assertThat(container.getContentType()).isNotBlank();
    assertThat(container.getInputStream()).isNotNull();
    assertThat(container.getInputStream().readAllBytes()).isEqualTo(TEST_CONTENT);
  }

  @Test
  void should_return_empty_file_container_when_file_does_not_exist() {
    // -- ACT --
    Optional<FileContainer> result =
        minioService.getFileContainerInTenant("nonexistent/missing-doc");

    // -- ASSERT --
    assertThat(result).isEmpty();
  }

  // -- DELETE --

  @Test
  void should_delete_file_in_tenant_path() throws Exception {
    // -- ARRANGE --
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    String fullPath =
        minioService.uploadFileInTenantPath(
            "to-delete/single-file.txt", data, TEST_CONTENT.length, TEST_CONTENT_TYPE);
    assertDoesNotThrow(() -> minioService.objectExists(fullPath));

    // -- ACT --
    minioService.deleteFileInTenantPath("to-delete/single-file.txt");

    // -- ASSERT --
    assertThrows(ErrorResponseException.class, () -> minioService.objectExists(fullPath));
  }

  @Test
  void should_delete_directory_in_tenant_path() throws Exception {
    // -- ARRANGE --
    String dir = TenantContext.getCurrentTenant() + "/exercises/ex-to-delete/";
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    minioService.uploadFileInTenantPath(
        dir + "file1.txt", data, TEST_CONTENT.length, TEST_CONTENT_TYPE);
    assertThat(minioService.countObjectsForCurrentTenant(dir)).isEqualTo(1);

    // -- ACT --
    minioService.deleteDirectoryInTenantPath(dir);

    // -- ASSERT --
    assertThat(minioService.countObjectsForCurrentTenant(dir)).isZero();
  }
}
