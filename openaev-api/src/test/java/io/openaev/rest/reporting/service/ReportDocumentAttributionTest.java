package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The report renderer stores its output document under the generation's tenant, captured on the
 * request thread, not under the ambient {@link TenantContext} of the render thread. Once {@code
 * documents} is activated the {@code TenantBaseListener} no longer stamps the row, so {@code
 * storeDocument} must attribute it explicitly. This pins that: with the ambient tenant set to a
 * different tenant than the job's, the row and its object still land under the job's tenant.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("The report renderer attributes its output document to the generation tenant")
class ReportDocumentAttributionTest extends IntegrationTest {

  @Autowired private PlaywrightReportingRenderer renderer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MinioService minioService;

  private String tenantA;
  private String tenantB;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("t22-report-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("t22-report-b").getId();
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
  @DisplayName("given_ambientTenantDiffersFromJobTenant_should_attributeRowAndObjectToJobTenant")
  void given_ambientTenantDiffersFromJobTenant_should_attributeRowAndObjectToJobTenant()
      throws Exception {
    // Arrange: the render runs on its own thread under the generation's tenant (B), while the
    // ambient context is set to another tenant (A) to prove the attribution does not read it.
    byte[] bytes = ("t22-report-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    PlaywrightReportingRenderer.RenderJob job =
        new PlaywrightReportingRenderer.RenderJob(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "t22-report",
            ReportingFormat.PDF,
            tenantB,
            "token");
    PlaywrightReportingRenderer.CapturedOutput output =
        new PlaywrightReportingRenderer.CapturedOutput(bytes, "pdf", "application/pdf");
    TenantContext.setCurrentTenant(tenantA);

    // Act
    String documentId = renderer.storeDocument(job, output).getId();
    uploadedObjects.add(new String[] {tenantB, target});

    // Assert
    assertEquals(
        tenantB,
        rowTenant(documentId),
        "the report output row must belong to the generation tenant (B), not the ambient tenant (A)");
    assertEquals(
        1,
        minioService.countObjects(tenantB + "/" + target),
        "the report bytes must be stored under the generation tenant's prefix");
    assertEquals(
        0,
        minioService.countObjects(tenantA + "/" + target),
        "the report bytes must not be stored under the ambient tenant's prefix");
  }

  @Test
  @DisplayName(
      "given_generationOwnedByTenantB_dispatchedUnderAnotherAmbient_should_attributeReportToTenantB")
  void
      given_generationOwnedByTenantB_dispatchedUnderAnotherAmbient_should_attributeReportToTenantB()
          throws Exception {
    // Arrange: a reporting and its generation persisted under tenant B, then the request thread's
    // ambient tenant switched to A. On the header route the ambient tenant is the default one, so a
    // job built from TenantContext would mis-attribute the report; it must take the generation's
    // own
    // tenant instead. The job is built through render()'s own seam, not hand-set as tenant B.
    Reporting reporting = new Reporting();
    reporting.setName("t22b-report");
    reporting.setContextType(ReportingContextType.PLATFORM);
    reporting.setTenant(new Tenant(tenantB));
    entityManager.persist(reporting);
    ReportingGeneration generation = new ReportingGeneration();
    generation.setReporting(reporting);
    generation.setFormat(ReportingFormat.PDF);
    generation.setStatus(ReportingGenerationStatus.PENDING);
    generation.setTenant(new Tenant(tenantB));
    entityManager.persist(generation);
    entityManager.flush();

    byte[] bytes = ("t22b-report-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    PlaywrightReportingRenderer.CapturedOutput output =
        new PlaywrightReportingRenderer.CapturedOutput(bytes, "pdf", "application/pdf");
    TenantContext.setCurrentTenant(tenantA);

    // Act: build the job the way render() does, then store the produced document.
    PlaywrightReportingRenderer.RenderJob job = renderer.toRenderJob(generation, "token");
    String documentId = renderer.storeDocument(job, output).getId();
    uploadedObjects.add(new String[] {tenantB, target});

    // Assert
    assertEquals(
        tenantB,
        job.tenantId(),
        "the render job must carry the generation tenant (B), not the ambient tenant (A)");
    assertEquals(
        tenantB,
        rowTenant(documentId),
        "the report output row must belong to the generation tenant (B), not the ambient tenant (A)");
    assertEquals(
        1,
        minioService.countObjects(tenantB + "/" + target),
        "the report bytes must be stored under the generation tenant's prefix");
    assertEquals(
        0,
        minioService.countObjects(tenantA + "/" + target),
        "the report bytes must not be stored under the ambient tenant's prefix");
  }

  private String rowTenant(String documentId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM documents WHERE document_id = ?1")
            .setParameter(1, documentId)
            .getSingleResult();
  }
}
