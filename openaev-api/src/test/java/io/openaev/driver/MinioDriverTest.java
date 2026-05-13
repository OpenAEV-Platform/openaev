package io.openaev.driver;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.*;
import io.minio.messages.Item;
import io.openaev.IntegrationTest;
import io.openaev.config.MinioConfig;
import io.openaev.config.S3Config;
import io.openaev.database.model.Tenant;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("MinioDriver")
@WithMockUser
class MinioDriverTest extends IntegrationTest {

  @Autowired private MinioDriver minioDriver;
  @Autowired private MinioConfig minioConfig;
  @Autowired private S3Config s3Config;

  private MinioClient minioClient;

  @BeforeAll
  void setUpAll() {
    minioClient = minioDriver.getMinioClient();
  }

  @Nested
  @DisplayName("getMinioClient")
  class GetMinioClient {

    @Test
    @DisplayName("Given valid config, should return a non-null MinioClient")
    void given_validConfig_should_returnNonNullClient() {
      // -- ARRANGE --
      // Config is provided by test application.properties (openaev-test-minio on port 11000)

      // -- ACT --
      MinioClient result = minioDriver.getMinioClient();

      // -- ASSERT --
      assertThat(result).isNotNull();
    }
  }

  @Nested
  @DisplayName("minioClient bean")
  class MinioClientBean {

    @Test
    @DisplayName("Given application started, should have created the bucket")
    void given_applicationStarted_should_haveCreatedBucket() throws Exception {
      // -- ARRANGE --
      String bucket = minioConfig.getBucket();

      // -- ACT --
      boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());

      // -- ASSERT --
      assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Given root-level file in bucket, should migrate it to default tenant path")
    void given_rootLevelFile_should_migrateToDefaultTenantPath() throws Exception {
      // -- ARRANGE --
      String bucket = minioConfig.getBucket();
      String rootFileName = "test-migrate-" + UUID.randomUUID() + ".txt";
      byte[] content = "test content".getBytes(StandardCharsets.UTF_8);

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucket).object(rootFileName).stream(
                  new ByteArrayInputStream(content), content.length, -1)
              .contentType("text/plain")
              .build());

      // -- ACT --
      // Trigger the migration logic via minioClient() bean creation
      MinioDriver freshDriver = new MinioDriver(minioConfig, s3Config, tenantRepository);
      freshDriver.minioClient();

      // -- ASSERT --
      String expectedPath = Tenant.DEFAULT_TENANT_UUID + "/" + rootFileName;
      StatObjectResponse stat =
          minioClient.statObject(
              StatObjectArgs.builder().bucket(bucket).object(expectedPath).build());
      assertThat(stat).isNotNull();
      assertThat(stat.size()).isEqualTo(content.length);

      // Cleanup
      minioClient.removeObject(
          RemoveObjectArgs.builder().bucket(bucket).object(expectedPath).build());
    }

    @Test
    @DisplayName("Given file already under tenant path, should not move it")
    void given_fileUnderTenantPath_should_notMoveIt() throws Exception {
      // -- ARRANGE --
      String bucket = minioConfig.getBucket();
      String tenantId = Tenant.DEFAULT_TENANT_UUID;
      String fileName = tenantId + "/test-no-move-" + UUID.randomUUID() + ".txt";
      byte[] content = "tenant content".getBytes(StandardCharsets.UTF_8);

      minioClient.putObject(
          PutObjectArgs.builder().bucket(bucket).object(fileName).stream(
                  new ByteArrayInputStream(content), content.length, -1)
              .contentType("text/plain")
              .build());

      // -- ACT --
      MinioDriver freshDriver = new MinioDriver(minioConfig, s3Config, tenantRepository);
      freshDriver.minioClient();

      // -- ASSERT --
      // File should still be at original path (not double-nested)
      StatObjectResponse stat =
          minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(fileName).build());
      assertThat(stat).isNotNull();

      // Verify no double-nested copy was created
      String doubleNested = Tenant.DEFAULT_TENANT_UUID + "/" + fileName;
      List<String> objects = new ArrayList<>();
      for (Result<Item> r :
          minioClient.listObjects(
              ListObjectsArgs.builder().bucket(bucket).prefix(doubleNested).build())) {
        objects.add(r.get().objectName());
      }
      assertThat(objects).isEmpty();

      // Cleanup
      minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(fileName).build());
    }
  }
}
