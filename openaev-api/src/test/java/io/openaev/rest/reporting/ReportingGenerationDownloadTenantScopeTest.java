package io.openaev.rest.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The reporting generation download serves the output document under the GENERATION's tenant, the
 * owner of the report lifecycle, not the document's own tenant (which would make the ownership
 * check tautological, always serving whatever tenant the document sits in). A generation and
 * document in the same tenant download normally.
 *
 * <p>When the generation and its document disagree on tenant, two nets refuse the download and each
 * is pinned by its own case. The download route carries no tenant selector, so the request scope is
 * every tenant the caller is a member of: a document in a tenant the caller does NOT belong to is
 * invisible to the request, the lazy reference cannot initialize inside the scoped transaction and
 * the endpoint answers 404 before any ownership check runs. A document in another tenant the caller
 * DOES belong to loads, and the owning-tenant check in {@code FileService.getFile(Document,
 * owningTenantId)}, passed the generation's tenant, refuses it. The second case is the one that
 * proves the check, since the first never reaches it.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so every read of the request is a statement the inspector
 * rewrites, and removed on teardown with the MinIO objects.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@WithMockUser(isAdmin = true)
@DisplayName("Reporting generation download serves the output under the generation's tenant")
class ReportingGenerationDownloadTenantScopeTest extends IntegrationTest {

  private static final String DOWNLOAD = "/api/reportings/generations/{generationId}/file";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String[]> uploadedObjects = new ArrayList<>();
  private final List<String[]> seededRows = new ArrayList<>();
  private final List<String> createdTenants = new ArrayList<>();

  @BeforeEach
  void grantDefaultMembership() {
    jdbc = new JdbcTemplate(dataSource);
    // The download resolves the generation under the ambient tenant (the default on this route).
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
  }

  @AfterEach
  void cleanup() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
    for (int i = seededRows.size() - 1; i >= 0; i--) {
      String[] row = seededRows.get(i);
      jdbc.update("DELETE FROM " + row[0] + " WHERE " + row[1] + " = ?", row[2]);
    }
    seededRows.clear();
    tenantHelper.deleteCommittedTenants(createdTenants.toArray(new String[0]));
    createdTenants.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_generationAndDocumentInSameTenant_should_serveTheObject")
  void given_generationAndDocumentInSameTenant_should_serveTheObject() throws Exception {
    // Arrange: a successful generation whose document sits in the same (default) tenant.
    byte[] bytes = ("report-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    uploadObject(DEFAULT_TENANT, target, bytes);
    String documentId = seedDocument(DEFAULT_TENANT, target);
    String generationId = seedSuccessfulGeneration(DEFAULT_TENANT, documentId);

    // Act & Assert
    mvc.perform(get(DOWNLOAD, generationId).with(csrf())).andExpect(status().isOk());
  }

  @Test
  @DisplayName("given_documentInATenantTheCallerBelongsTo_should_refuseItByTheGenerationTenant")
  void given_documentInATenantTheCallerBelongsTo_should_refuseItByTheGenerationTenant()
      throws Exception {
    // Arrange: the generation is in the default tenant, but its document (anomalously) belongs to
    // another tenant of the caller, with its object stored there. The request scope holds both
    // tenants, so the document loads: only the generation's tenant, passed as the owning tenant,
    // refuses it. Passing the document's own tenant would serve it regardless.
    String otherTenant = createTenant("report-anomaly-member", true);
    byte[] bytes = ("report-anomaly-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    uploadObject(otherTenant, target, bytes);
    String documentId = seedDocument(otherTenant, target);
    String generationId = seedSuccessfulGeneration(DEFAULT_TENANT, documentId);

    // Act & Assert
    mvc.perform(get(DOWNLOAD, generationId).with(csrf())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("given_documentInATenantTheCallerDoesNotBelongTo_should_notFindIt")
  void given_documentInATenantTheCallerDoesNotBelongTo_should_notFindIt() throws Exception {
    // Arrange: same anomaly, but the document's tenant is outside the caller's memberships, so
    // the scoped request never sees the row and the lazy reference fails to initialize.
    String foreignTenant = createTenant("report-anomaly-foreign", false);
    byte[] bytes = ("report-anomaly-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    uploadObject(foreignTenant, target, bytes);
    String documentId = seedDocument(foreignTenant, target);
    String generationId = seedSuccessfulGeneration(DEFAULT_TENANT, documentId);

    // Act & Assert
    mvc.perform(get(DOWNLOAD, generationId).with(csrf())).andExpect(status().isNotFound());
  }

  private String createTenant(String name, boolean withCurrentUser) throws Exception {
    String id = tenantHelper.createTenantWithCurrentUser(name).getId();
    createdTenants.add(id);
    if (!withCurrentUser) {
      // Creating a tenant attaches its creator to it (TenantUserService.createDependencyForTenant),
      // so a tenant the caller does not belong to has to be detached explicitly, cache included.
      String userId = testUserHolder.get().getId();
      jdbc.update("DELETE FROM users_tenants WHERE user_id = ? AND tenant_id = ?", userId, id);
      tenantMembershipCacheManager.evict(userId, id);
      assertFalse(
          tenantMembershipCacheManager.findTenantIdsByUserId(userId).contains(id),
          "the caller must not be a member of the tenant the document is seeded in");
    }
    // Onboarding leaves the new tenant on the test thread, and the generation is resolved under
    // the ambient tenant: it must be the default one here, or the generation is not found and the
    // 404 comes from that lookup instead of from the net under test.
    TenantContext.clearCurrentTenant();
    assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
    return id;
  }

  private void uploadObject(String tenantId, String target, byte[] bytes) throws Exception {
    minioService.uploadFileForTenant(
        tenantId,
        target,
        new ByteArrayInputStream(bytes),
        bytes.length,
        MediaType.APPLICATION_PDF_VALUE);
    uploadedObjects.add(new String[] {tenantId, target});
  }

  private String seedDocument(String tenantId, String target) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO documents (document_id, document_name, document_target, document_type,"
            + " tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        "report-" + UUID.randomUUID() + ".pdf",
        target,
        MediaType.APPLICATION_PDF_VALUE,
        tenantId);
    seededRows.add(new String[] {"documents", "document_id", id});
    return id;
  }

  private String seedSuccessfulGeneration(String tenantId, String documentId) {
    String reportingId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, ?, ?, ?)",
        reportingId,
        "report-template-" + UUID.randomUUID(),
        ReportingContextType.PLATFORM.name(),
        tenantId);
    seededRows.add(new String[] {"reportings", "reporting_id", reportingId});
    String generationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
            + " reporting_generation_status, reporting_generation_format, document_id, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        generationId,
        reportingId,
        ReportingGenerationStatus.SUCCESS.name(),
        ReportingFormat.PDF.name(),
        documentId,
        tenantId);
    seededRows.add(new String[] {"reporting_generations", "reporting_generation_id", generationId});
    return generationId;
  }
}
