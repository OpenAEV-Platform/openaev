package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentApi.DOCUMENT_API;
import static io.openaev.rest.document.DocumentApi.TENANT_DOCUMENT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Executable;
import io.openaev.database.model.FileDrop;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A document used by a payload (file drop or executable) cannot be deleted. The check reads the
 * document's payload associations, and payloads still take the ambient tenant, which on the
 * non-prefixed route is the default one while the document is scoped to the header tenant. The
 * refusal must hold on both routes: a document of B used by a payload of B stays in place whatever
 * the ambient tenant.
 *
 * <p>Rows are written through the repositories and the persistence context is cleared, so the
 * delete loads the document and its associations from the database. Ground truth is read with raw
 * JDBC, which the statement inspector never rewrites.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("A document used by a payload of its tenant cannot be deleted on either route")
class DocumentDeleteInUseScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PayloadRepository payloadRepository;

  private String tenantB;

  @BeforeEach
  void seedCallerInBothTenants() throws Exception {
    // The caller belongs to the default tenant and to B, so the header selects B while the ambient
    // tenant stays the default.
    tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("doc-in-use-b").getId();
    // Onboarding leaves B on the test thread, and the request thread of the header route carries
    // none: the ambient tenant must fall back to the default one before the header requests.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route")
  class HeaderRoute {

    @Test
    @DisplayName(
        "given a document of B dropped by a payload of B when deleted via the header then it is"
            + " refused and kept")
    void given_fileDropInUseOnHeaderRoute_should_refuseAndKeepDocument() throws Exception {
      // -- Arrange --
      String documentId = seedDocumentUsedByFileDrop();

      // -- Act --
      mvc.perform(
              delete(DOCUMENT_API + "/{documentId}", documentId)
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          .andExpect(status().isBadRequest());

      // -- Assert --
      assertThat(rowExists(documentId)).isTrue();
    }

    @Test
    @DisplayName(
        "given a document of B run by an executable of B when deleted via the header then it is"
            + " refused and kept")
    void given_executableInUseOnHeaderRoute_should_refuseAndKeepDocument() throws Exception {
      // -- Arrange --
      String documentId = seedDocumentUsedByExecutable();

      // -- Act --
      mvc.perform(
              delete(DOCUMENT_API + "/{documentId}", documentId)
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          .andExpect(status().isBadRequest());

      // -- Assert --
      assertThat(rowExists(documentId)).isTrue();
    }

    @Test
    @DisplayName("given an unused document of B when deleted via the header then it is removed")
    void given_unusedDocumentOnHeaderRoute_should_deleteDocument() throws Exception {
      // -- Arrange --
      String documentId = seedDocumentInB();

      // -- Act --
      mvc.perform(
              delete(DOCUMENT_API + "/{documentId}", documentId)
                  .header(TENANT_HEADER, tenantB)
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      // Proves the refusals above come from the payload check, not from a delete that never runs.
      assertThat(rowExists(documentId)).isFalse();
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a document of B dropped by a payload of B when deleted under the path of B then it"
            + " is refused and kept")
    void given_fileDropInUseOnPrefixedRoute_should_refuseAndKeepDocument() throws Exception {
      // -- Arrange --
      String documentId = seedDocumentUsedByFileDrop();

      // -- Act --
      mvc.perform(prefixed(documentId)).andExpect(status().isBadRequest());

      // -- Assert --
      assertThat(rowExists(documentId)).isTrue();
    }

    @Test
    @DisplayName(
        "given a document of B run by an executable of B when deleted under the path of B then it"
            + " is refused and kept")
    void given_executableInUseOnPrefixedRoute_should_refuseAndKeepDocument() throws Exception {
      // -- Arrange --
      String documentId = seedDocumentUsedByExecutable();

      // -- Act --
      mvc.perform(prefixed(documentId)).andExpect(status().isBadRequest());

      // -- Assert --
      assertThat(rowExists(documentId)).isTrue();
    }
  }

  // -- Helpers --

  private MockHttpServletRequestBuilder prefixed(String documentId) {
    return delete(TENANT_DOCUMENT_API + "/{documentId}", tenantB, documentId).with(csrf());
  }

  private Document newDocumentInB() {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setTenant(new Tenant(tenantB));
    document.setName("doc-in-use-" + UUID.randomUUID());
    document.setTarget(UUID.randomUUID() + ".jpg");
    return document;
  }

  private String seedDocumentInB() {
    String documentId = documentRepository.save(newDocumentInB()).getId();
    entityManager.flush();
    entityManager.clear();
    return documentId;
  }

  /** The document instance is kept in memory: the test transaction has no scope to read it back. */
  private String seedDocumentUsedByFileDrop() {
    Document document = documentRepository.save(newDocumentInB());
    FileDrop payload = (FileDrop) PayloadFixture.createDefaultFileDrop();
    payload.setId(UUID.randomUUID().toString());
    payload.setFileDropFile(document);
    return seedPayloadInB(payload, document);
  }

  private String seedDocumentUsedByExecutable() {
    Document document = documentRepository.save(newDocumentInB());
    Executable payload = (Executable) PayloadFixture.createDefaultExecutable(document);
    payload.setId(UUID.randomUUID().toString());
    return seedPayloadInB(payload, document);
  }

  private String seedPayloadInB(Payload payload, Document document) {
    payload.setTenant(new Tenant(tenantB));
    payloadRepository.save(payload);
    entityManager.flush();
    entityManager.clear();
    return document.getId();
  }

  /** Raw JDBC on the test connection: the statement inspector never rewrites it. */
  private boolean rowExists(String documentId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement("SELECT 1 FROM documents WHERE document_id = ?")) {
                statement.setString(1, documentId);
                try (ResultSet resultSet = statement.executeQuery()) {
                  return resultSet.next();
                }
              }
            });
  }
}
