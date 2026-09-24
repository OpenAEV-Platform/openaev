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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The report renderer stores its output document under the generation's tenant, captured on the
 * request thread, not under the ambient {@link TenantContext} of the render thread. Once {@code
 * documents} is activated the {@code TenantBaseListener} no longer stamps the row, so {@code
 * storeDocument} attributes it explicitly and persists it through the background primitive ({@code
 * TxCtx.forTenant} via {@code TenantScopedJobRunner}). This pins that: with the ambient tenant set
 * to a different tenant than the job's, the row and its object still land under the job's tenant.
 *
 * <p>Deliberately NOT {@code @Transactional}: {@code storeDocument} opens a background transaction
 * through the primitive, which refuses to run inside an active one (production runs it on the
 * render executor thread, with no ambient transaction). Tenants are seeded in auto-committed JDBC
 * and the written rows/objects are cleaned up explicitly, mirroring {@code
 * TenantScopedTransactionIntegrationTest}.
 *
 * <p>{@code @TestPropertySource} activates {@code documents} for this test only (the test classpath
 * keeps the allowlist empty), so the write runs against the statement inspector, the production
 * configuration once {@code documents} is v2-active. The row tenant comes from the explicit {@code
 * setTenant} on the render thread (the persistence listener is gone since activation) and is read
 * back with raw JDBC, which the inspector never rewrites.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@DisplayName("The report renderer attributes its output document to the generation tenant")
class ReportDocumentAttributionTest extends IntegrationTest {

  @Autowired private PlaywrightReportingRenderer renderer;
  @Autowired private MinioService minioService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("t22-report-a-" + UUID.randomUUID());
    tenantB = seedTenant("t22-report-b-" + UUID.randomUUID());
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
    jdbc.update("DELETE FROM documents WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
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
    // Arrange: a generation owned by tenant B, then the request thread's ambient tenant switched to
    // A. In production the two coincide today (reporting_generations is v1, stamped from the
    // ambient tenant); once it activates, the row carries the write tenant while the ambient one
    // on the header route stays the default, so toRenderJob must read the generation. The graph
    // is built in memory (toRenderJob reads its fields, no DB needed) so the document write under
    // test is the first committed row.
    Reporting reporting = new Reporting();
    reporting.setId(UUID.randomUUID().toString());
    reporting.setName("t22b-report");
    reporting.setContextType(ReportingContextType.PLATFORM);
    reporting.setTenant(new Tenant(tenantB));
    ReportingGeneration generation = new ReportingGeneration();
    generation.setId(UUID.randomUUID().toString());
    generation.setReporting(reporting);
    generation.setFormat(ReportingFormat.PDF);
    generation.setStatus(ReportingGenerationStatus.PENDING);
    generation.setTenant(new Tenant(tenantB));

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

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private String rowTenant(String documentId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM documents WHERE document_id = ?", String.class, documentId);
  }
}
