package io.openaev.rest.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant isolation for the dedicated agent download route (GET
 * /api/tenants/{tenantId}/documents/{documentId}/agent-file).
 *
 * <p>The route resolves the document by primary key ({@code documentService.document(documentId)}),
 * and Hibernate's {@code tenantFilter} never applies to primary-key loads, so the raw lookup can
 * return another tenant's row. The cross-tenant read is closed by {@code
 * DocumentApi#assertDocumentInRequestScope}: the loaded document's tenant is checked against the
 * request scope (the path tenant), and a mismatch is surfaced as 404. This test pins that behaviour
 * on the brand-new agent route so it cannot regress into the cross-tenant defect tracked for the
 * legacy /file route in #294. Runs as admin so RBAC is bypassed and only the tenant boundary is
 * under test.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("agent document download tenant isolation through the real HTTP endpoint")
class DocumentAgentFileHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_AGENT_FILE =
      "/api/tenants/{tenantId}/documents/{documentId}/agent-file";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String documentB;

  @BeforeEach
  void seedTwoTenantsWithADocumentInTenantB() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("doc-agent-iso-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("doc-agent-iso-b").getId();
    documentB = seedDocument(tenantB, "doc-b");
  }

  @Test
  @DisplayName(
      "under tenant A's path: tenant B's document is not downloadable via /agent-file (404)")
  void tenantACannotDownloadTenantBDocumentViaAgentFile() throws Exception {
    // Primary-key load would happily return B's row; assertDocumentInRequestScope must refuse it
    // because B's tenant is not in tenant A's request scope. 404, never the file.
    mvc.perform(get(TENANT_AGENT_FILE, tenantA, documentB)).andExpect(status().isNotFound());
  }

  private String seedDocument(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO documents (document_id, document_name, document_target, document_type,"
                + " tenant_id) VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, name + "-target")
        .setParameter(4, "text/plain")
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }
}
