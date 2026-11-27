package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinioServiceTest extends IntegrationTest {

  @Autowired private MinioService minioService;
  @Autowired private MinioClient minioClient;

  private static final String TENANT = UUID.randomUUID().toString();
  private static final String TEST_FILE_NAME = "documents/test-report.pdf";
  private static final String TEST_CONTENT_TYPE = "application/pdf";
  private static final byte[] TEST_CONTENT = "test-file-content".getBytes(StandardCharsets.UTF_8);

  // -- CREATE/UPDATE --

  @Test
  void should_upload_file_under_the_named_tenant() throws Exception {
    // -- ACT --
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    String fullPath =
        minioService.uploadFileForTenant(
            TENANT, TEST_FILE_NAME, data, TEST_CONTENT.length, TEST_CONTENT_TYPE);

    // -- ASSERT --
    assertThat(fullPath).isEqualTo(TENANT + "/" + TEST_FILE_NAME);
    assertDoesNotThrow(() -> minioService.objectExists(fullPath));
  }

  @Test
  void should_upload_stream_with_filename_metadata() throws Exception {
    // -- ARRANGE --
    String file = "streams/stream-uuid-001";
    String originalName = "quarterly-report.xlsx";
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);

    // -- ACT --
    String fullPath = minioService.uploadStreamForTenant(TENANT, file, originalName, data);

    // -- ASSERT --
    assertDoesNotThrow(() -> minioService.objectExists(fullPath));
  }

  // -- READ --

  @Test
  void should_get_file_under_the_named_tenant() throws Exception {
    // -- ARRANGE --
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    minioService.uploadFileForTenant(
        TENANT, "readable/test-read.txt", data, TEST_CONTENT.length, TEST_CONTENT_TYPE);

    // -- ACT --
    Optional<InputStream> result =
        minioService.getFilePathForTenant(TENANT, "readable/test-read.txt");

    // -- ASSERT --
    assertThat(result).isPresent();
    byte[] readBytes = result.get().readAllBytes();
    assertThat(readBytes).isEqualTo(TEST_CONTENT);
  }

  @Test
  void should_return_empty_when_file_does_not_exist() {
    // -- ACT --
    Optional<InputStream> result =
        minioService.getFilePathForTenant(TENANT, "nonexistent/ghost.bin");

    // -- ASSERT --
    assertThat(result).isEmpty();
  }

  @Test
  void should_get_file_container_with_metadata() throws Exception {
    // -- ARRANGE --
    String file = "containers/doc-uuid-42";
    String originalName = "report.docx";
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    minioService.uploadStreamForTenant(TENANT, file, originalName, data);

    // -- ACT --
    Optional<FileContainer> result = minioService.getFileContainerForTenant(TENANT, file);

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
        minioService.getFileContainerForTenant(TENANT, "nonexistent/missing-doc");

    // -- ASSERT --
    assertThat(result).isEmpty();
  }

  // -- DELETE --

  @Test
  void should_delete_file_under_the_named_tenant() throws Exception {
    // -- ARRANGE --
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    String fullPath =
        minioService.uploadFileForTenant(
            TENANT, "to-delete/single-file.txt", data, TEST_CONTENT.length, TEST_CONTENT_TYPE);
    assertDoesNotThrow(() -> minioService.objectExists(fullPath));

    // -- ACT --
    minioService.deleteFileForTenant(TENANT, "to-delete/single-file.txt");

    // -- ASSERT --
    assertThrows(ErrorResponseException.class, () -> minioService.objectExists(fullPath));
  }

  @Test
  void should_delete_directory_under_the_named_tenant() throws Exception {
    // -- ARRANGE --
    String dir = "exercises/ex-to-delete/";
    InputStream data = new ByteArrayInputStream(TEST_CONTENT);
    minioService.uploadFileForTenant(
        TENANT, dir + "file1.txt", data, TEST_CONTENT.length, TEST_CONTENT_TYPE);
    assertThat(minioService.countObjects(TENANT + "/" + dir)).isEqualTo(1);

    // -- ACT --
    minioService.deleteDirectoryForTenant(TENANT, dir);

    // -- ASSERT --
    assertThat(minioService.countObjects(TENANT + "/" + dir)).isZero();
  }

  // -- KEYS --

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void given_noTenant_should_refuseEveryKey(String tenantId) {
    // -- ACT / ASSERT --
    assertThrows(
        RuntimeException.class,
        () ->
            minioService.uploadFileForTenant(
                tenantId,
                TEST_FILE_NAME,
                new ByteArrayInputStream(TEST_CONTENT),
                TEST_CONTENT.length,
                TEST_CONTENT_TYPE));
    assertThrows(
        RuntimeException.class,
        () ->
            minioService.uploadStreamForTenant(
                tenantId, TEST_FILE_NAME, "name", new ByteArrayInputStream(TEST_CONTENT)));
    assertThrows(
        RuntimeException.class, () -> minioService.getFilePathForTenant(tenantId, TEST_FILE_NAME));
    assertThrows(
        RuntimeException.class,
        () -> minioService.getFileContainerForTenant(tenantId, TEST_FILE_NAME));
    assertThrows(
        RuntimeException.class, () -> minioService.deleteFileForTenant(tenantId, TEST_FILE_NAME));
    assertThrows(
        RuntimeException.class, () -> minioService.deleteDirectoryForTenant(tenantId, "documents"));
  }

  @Test
  void given_noObjectName_should_readNothingAndDeleteNothing() {
    // -- ACT --
    Optional<InputStream> file = minioService.getFilePathForTenant(TENANT, null);
    Optional<FileContainer> container = minioService.getFileContainerForTenant(TENANT, null);

    // -- ASSERT --
    assertThat(file).isEmpty();
    assertThat(container).isEmpty();
    assertDoesNotThrow(() -> minioService.deleteDirectoryForTenant(TENANT, null));
  }

  // -- HEALTH CHECK --

  @Test
  void should_pass_the_access_check_on_a_reachable_bucket() {
    // -- ACT & ASSERT --
    assertDoesNotThrow(() -> minioService.checkStorageAccessible(minioClient));
  }

  @Test
  void should_probe_the_default_tenant_prefix() throws Exception {
    // -- ARRANGE --
    MinioClient client = mock(MinioClient.class);
    when(client.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());

    // -- ACT --
    minioService.checkStorageAccessible(client);

    // -- ASSERT --
    ArgumentCaptor<ListObjectsArgs> args = ArgumentCaptor.forClass(ListObjectsArgs.class);
    verify(client).listObjects(args.capture());
    assertThat(args.getValue().prefix()).isEqualTo(Tenant.DEFAULT_TENANT_UUID + "/");
  }
}
