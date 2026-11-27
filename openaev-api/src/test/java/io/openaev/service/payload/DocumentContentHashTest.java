package io.openaev.service.payload;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Document;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.document.form.DocumentCreateInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Document content hash")
class DocumentContentHashTest extends IntegrationTest {

  @Autowired private DocumentService documentService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private TenantScopedTransaction tenantTx;

  @Test
  @DisplayName("is set on upload, and read once then stored when missing")
  void given_uploadedDocument_should_carryItsContentHash() throws Exception {
    // Arrange
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(DEFAULT_TENANT_UUID));
    byte[] content = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
    String expected = DigestUtils.sha256Hex(content);
    Document uploaded =
        documentService.upsert(
            "tool-" + UUID.randomUUID() + ".bin",
            new ByteArrayInputStream(content),
            content.length,
            "application/octet-stream",
            new DocumentCreateInput(),
            DEFAULT_TENANT_UUID);
    assertThat(uploaded.getContentHash()).isEqualTo(expected);
    uploaded.setContentHash(null);
    documentRepository.save(uploaded);

    // Act
    String hash = documentService.contentHash(uploaded).orElseThrow();

    // Assert
    assertThat(hash).isEqualTo(expected);
    entityManager.flush();
    entityManager.clear();
    assertThat(documentRepository.findById(uploaded.getId()).orElseThrow().getContentHash())
        .isEqualTo(expected);
  }
}
