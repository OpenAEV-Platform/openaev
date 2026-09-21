package io.openaev.rest.reporting;

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
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reporting generation download serves the output document under the GENERATION's tenant, the
 * owner of the report lifecycle, not the document's own tenant (which would make the ownership
 * check tautological, always serving whatever tenant the document sits in). When the generation and
 * its document disagree on tenant, the object is treated as missing (a 404), fail-closed on the
 * anomaly. A generation and document in the same tenant still download normally.
 *
 * <p>It does not arm {@code documents}: the refusal is the application-level owning-tenant check in
 * {@code FileService.getFile(Document, owningTenantId)}, passed the generation's tenant, which runs
 * on the loaded entity regardless of the statement inspector, so arming would not change the
 * outcome.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Reporting generation download serves the output under the generation's tenant")
class ReportingGenerationDownloadTenantScopeTest extends IntegrationTest {

  private static final String DOWNLOAD = "/api/reportings/generations/{generationId}/file";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void grantDefaultMembership() {
    // The download resolves the generation under the ambient tenant (the default on this route).
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
  }

  @AfterEach
  void clearContext() {
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
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
  @DisplayName("given_documentInAnotherTenantThanGeneration_should_return404")
  void given_documentInAnotherTenantThanGeneration_should_return404() throws Exception {
    // Arrange: the generation is in the default tenant, but its document (anomalously) belongs to
    // another tenant, with its object stored there. Passing the document's own tenant would serve
    // it
    // regardless; passing the generation's tenant refuses the mismatch.
    String otherTenant = tenantHelper.createTenantWithCurrentUser("report-anomaly").getId();
    byte[] bytes = ("report-anomaly-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    uploadObject(otherTenant, target, bytes);
    String documentId = seedDocument(otherTenant, target);
    String generationId = seedSuccessfulGeneration(DEFAULT_TENANT, documentId);

    // Act & Assert
    mvc.perform(get(DOWNLOAD, generationId).with(csrf())).andExpect(status().isNotFound());
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
    entityManager
        .createNativeQuery(
            "INSERT INTO documents (document_id, document_name, document_target, document_type,"
                + " tenant_id) VALUES (CAST(:id AS uuid), :name, :target, :type,"
                + " CAST(:tenant AS uuid))")
        .setParameter("id", id)
        .setParameter("name", "report-" + UUID.randomUUID() + ".pdf")
        .setParameter("target", target)
        .setParameter("type", MediaType.APPLICATION_PDF_VALUE)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  private String seedSuccessfulGeneration(String tenantId, String documentId) {
    String reportingId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type,"
                + " tenant_id) VALUES (CAST(:id AS uuid), :name, :contextType,"
                + " CAST(:tenant AS uuid))")
        .setParameter("id", reportingId)
        .setParameter("name", "report-template-" + UUID.randomUUID())
        .setParameter("contextType", ReportingContextType.PLATFORM.name())
        .setParameter("tenant", tenantId)
        .executeUpdate();
    String generationId = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
                + " reporting_generation_status, reporting_generation_format, document_id,"
                + " tenant_id) VALUES (CAST(:id AS uuid), CAST(:reporting AS uuid), :status,"
                + " :format, CAST(:document AS uuid), CAST(:tenant AS uuid))")
        .setParameter("id", generationId)
        .setParameter("reporting", reportingId)
        .setParameter("status", ReportingGenerationStatus.SUCCESS.name())
        .setParameter("format", ReportingFormat.PDF.name())
        .setParameter("document", documentId)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
    return generationId;
  }
}
