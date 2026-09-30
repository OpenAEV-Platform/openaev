package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.Tenant;
import io.openaev.service.MinioService;
import io.openaev.utils.fixtures.ReportingFixture;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * After the render thread stores the produced document, it re-reads the generation and saves it
 * with the (detached) document attached. That completion runs on the render thread under the v1
 * {@link TenantContext} only, with no v2 primitive scope. This pins, with {@code documents}
 * v2-active, that attaching and saving the document does NOT reach the now-scoped {@code documents}
 * table through the statement inspector (a {@code @ManyToOne} FK write on the still-v1 {@code
 * reporting_generations} row does not read the association back), so the completion does not
 * fail-close on the render thread and the generation ends SUCCESS carrying its document.
 *
 * <p>{@code @TestPropertySource} activates {@code documents} for this test only. The class is NOT
 * {@code @Transactional}: {@code storeDocument} opens a background primitive transaction, which
 * refuses to run inside an active one, and the completion opens its own transactions exactly as it
 * does on the render thread. Seeding is committed and cleaned up by tenant on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@DisplayName("The render completion attaches the stored document without a v2 scope on the thread")
class ReportCompletionScopeTest extends IntegrationTest {

  @Autowired private PlaywrightReportingRenderer renderer;
  @Autowired private MinioService minioService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private String generationId;

  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedPendingGeneration() {
    jdbc = new JdbcTemplate(dataSource);
    tenantB = seedTenant("report-completion-b-" + UUID.randomUUID());
    seedPendingGenerationForTenant(tenantB);
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
    jdbc.update("DELETE FROM reporting_generations WHERE tenant_id = ?", tenantB);
    jdbc.update("DELETE FROM reportings WHERE tenant_id = ?", tenantB);
    jdbc.update("DELETE FROM documents WHERE tenant_id = ?", tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_documentsActiveAndNoV2ScopeOnThread_should_completeGenerationWithTheDocument")
  void given_documentsActiveAndNoV2ScopeOnThread_should_completeGenerationWithTheDocument()
      throws Exception {
    // Arrange: the render thread runs under the job tenant's v1 TenantContext (no primitive scope),
    // exactly as execute() sets it before storing and completing.
    TenantContext.setCurrentTenant(tenantB);
    byte[] bytes = ("report-completion-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    String target = DigestUtils.md5Hex(bytes) + ".pdf";
    PlaywrightReportingRenderer.RenderJob job =
        new PlaywrightReportingRenderer.RenderJob(
            generationId,
            UUID.randomUUID().toString(),
            "report-completion",
            ReportingFormat.PDF,
            tenantB,
            "token");
    PlaywrightReportingRenderer.CapturedOutput output =
        new PlaywrightReportingRenderer.CapturedOutput(bytes, "pdf", "application/pdf");

    // Act: store the document (its own primitive scope) then complete the generation with it, both
    // on the render thread, with documents v2-active.
    Document document = renderer.storeDocument(job, output);
    uploadedObjects.add(new String[] {tenantB, target});
    renderer.completeWithSuccess(job, document);

    // Assert: the generation reached SUCCESS carrying the stored document; the completion did not
    // fail-close on the documents table and the document is not left orphaned.
    assertEquals(
        ReportingGenerationStatus.SUCCESS.name(),
        generationStatus(generationId),
        "the completion must flip the generation to SUCCESS on the render thread");
    assertEquals(
        document.getId(),
        generationDocumentId(generationId),
        "the completion must attach the stored document to the generation");
    assertEquals(
        tenantB,
        documentRowTenant(document.getId()),
        "the stored document row must belong to the job tenant");
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

  private void seedPendingGenerationForTenant(String tenantId) {
    rawTransaction()
        .execute(
            status -> {
              Reporting reporting = ReportingFixture.createDefaultReporting();
              reporting.setTenant(new Tenant(tenantId));
              entityManager.persist(reporting);

              ReportingGeneration generation = new ReportingGeneration();
              generation.setReporting(reporting);
              generation.setFormat(ReportingFormat.PDF);
              generation.setGenerationTrigger(ReportingGenerationTrigger.MANUAL);
              generation.setStatus(ReportingGenerationStatus.PENDING);
              generation.setTenant(new Tenant(tenantId));
              entityManager.persist(generation);

              entityManager.flush();
              generationId = generation.getId();
              return null;
            });
  }

  private String generationStatus(String id) {
    return jdbc.queryForObject(
        "SELECT reporting_generation_status FROM reporting_generations WHERE"
            + " reporting_generation_id = ?",
        String.class,
        id);
  }

  private String generationDocumentId(String id) {
    return jdbc.queryForObject(
        "SELECT document_id FROM reporting_generations WHERE reporting_generation_id = ?",
        String.class,
        id);
  }

  private String documentRowTenant(String documentId) {
    return jdbc.queryForObject(
        "SELECT tenant_id FROM documents WHERE document_id = ?", String.class, documentId);
  }

  private TransactionTemplate rawTransaction() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return template;
  }
}
