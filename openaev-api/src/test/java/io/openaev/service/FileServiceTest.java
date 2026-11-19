package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileServiceTest extends IntegrationTest {

  @Autowired private FileService fileService;
  @Autowired private MinioService minioService;

  private static final byte[] CONTENT = "attachment-bytes".getBytes(StandardCharsets.UTF_8);

  @Test
  void should_upload_stream_for_an_explicit_tenant_with_a_self_describing_key() throws Exception {
    // -- ARRANGE --
    String tenantId = UUID.randomUUID().toString();
    String path = "/communications/c1";
    String fileName = "report.txt";
    InputStream data = new ByteArrayInputStream(CONTENT);

    // -- ACT --
    String key = fileService.uploadStream(tenantId, path, fileName, data);

    // -- ASSERT: the key is prefixed with the tenant it was written under --
    assertThat(key).isEqualTo(tenantId + path + "/" + fileName);
    assertThat(minioService.getFileContainerForTenant(tenantId, key.substring(tenantId.length())))
        .isPresent();
  }

  @Test
  void given_selfDescribingKey_should_readCommunicationAttachmentUnderItsOwnTenant()
      throws Exception {
    // -- ARRANGE --
    String tenantId = UUID.randomUUID().toString();
    String key =
        fileService.uploadStream(
            tenantId,
            "/communications/c2",
            "selfdescribing.txt",
            new ByteArrayInputStream(CONTENT));

    // -- ACT --
    Optional<FileContainer> result = fileService.getCommunicationAttachment(key, tenantId);

    // -- ASSERT --
    assertThat(result).isPresent();
    assertThat(result.get().getInputStream().readAllBytes()).isEqualTo(CONTENT);
  }

  @Test
  void given_legacyBareKey_should_readCommunicationAttachmentUnderTheDefaultTenant()
      throws Exception {
    // -- ARRANGE: a key written before the tenant-explicit overload existed, always physically
    // under the default tenant (the only one the IMAP background thread used at the time),
    // whatever the real owning tenant of the row that references it is.
    String legacyKey = "/communications/c3/legacy.txt";
    minioService.uploadStreamForTenant(
        Tenant.DEFAULT_TENANT_UUID, legacyKey, "legacy.txt", new ByteArrayInputStream(CONTENT));
    String realOwnerTenantId = UUID.randomUUID().toString();

    // -- ACT: the owner tenant passed in is NOT where the legacy object physically lives --
    Optional<FileContainer> result =
        fileService.getCommunicationAttachment(legacyKey, realOwnerTenantId);

    // -- ASSERT: still resolved, from the default tenant path the legacy writer actually used --
    assertThat(result).isPresent();
    assertThat(result.get().getInputStream().readAllBytes()).isEqualTo(CONTENT);
  }

  @Test
  void given_selfDescribingKeyOfAnotherTenant_should_notReadCommunicationAttachment()
      throws Exception {
    // -- ARRANGE --
    String writerTenantId = UUID.randomUUID().toString();
    String key =
        fileService.uploadStream(
            writerTenantId,
            "/communications/c4",
            "cross-tenant.txt",
            new ByteArrayInputStream(CONTENT));
    String otherTenantId = UUID.randomUUID().toString();

    // -- ACT: resolving with a tenant the key was not written under never finds the real object --
    Optional<FileContainer> result = fileService.getCommunicationAttachment(key, otherTenantId);

    // -- ASSERT --
    assertThat(result).isEmpty();
  }
}
