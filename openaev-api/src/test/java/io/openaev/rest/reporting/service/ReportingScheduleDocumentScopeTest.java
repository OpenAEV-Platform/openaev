package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.ReportingGenerationTrigger;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.utils.fixtures.ReportingFixture;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The reporting scheduling engine polls a generation with {@code findWithDocumentByIdAndTenantId},
 * a JOIN FETCH on the now-active {@code documents} table, and emails the produced document. Once
 * {@code documents} is v2-active the statement inspector scopes that join, so the poll must run
 * under the schedule tenant's v2 scope or the document fail-closes and the delivery is skipped with
 * a document-less generation ("generation succeeded without a document").
 *
 * <p>This pins the conversion on the real stack: the schedule tenant scopes the poll read through
 * the primitive ({@code TxCtx.forTenant} via {@code TenantScopedJobRunner}), so the produced
 * document is found; the same query without a v2 scope fail-closes the join and returns a
 * document-less generation, proving the scope is the difference and not the row itself.
 *
 * <p>{@code @TestPropertySource} activates {@code documents} for this test only. The class is NOT
 * {@code @Transactional}: the primitive refuses to open inside an active transaction (a scope
 * boundary is explicit), so seeding and cleanup run in auto-committed JDBC / a committed {@code
 * TransactionTemplate}, mirroring {@code TenantScopedTransactionIntegrationTest}.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=documents")
@DisplayName("Reporting schedule delivery reads the produced document under the schedule tenant")
class ReportingScheduleDocumentScopeTest extends IntegrationTest {

  @Autowired private ReportingScheduleService scheduleService;
  @Autowired private ReportingGenerationRepository reportingGenerationRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private String generationId;
  private String documentId;

  @BeforeEach
  void seedSuccessfulGenerationForTenantB() {
    jdbc = new JdbcTemplate(dataSource);
    tenantB = seedTenant("reporting-doc-scope-b-" + UUID.randomUUID());
    seedSuccessfulGeneration(tenantB);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM reporting_generations WHERE tenant_id = ?", tenantB);
    jdbc.update("DELETE FROM reportings WHERE tenant_id = ?", tenantB);
    jdbc.update("DELETE FROM documents WHERE tenant_id = ?", tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("The poll read that feeds the delivery")
  class PollRead {

    @Test
    @DisplayName("the schedule tenant scopes the poll, so the produced document is found")
    void given_activeDocuments_should_findTheProducedDocumentUnderScheduleTenant() {
      // -- Act -- the poll runs inside the primitive scoped to the schedule tenant
      ReportingGeneration terminal = scheduleService.awaitTerminalStatus(generationId, tenantB);

      // -- Assert -- the generation and its document are both resolved for the delivery
      assertNotNull(terminal, "the terminal generation must be read");
      assertEquals(ReportingGenerationStatus.SUCCESS, terminal.getStatus());
      assertNotNull(
          terminal.getDocument(),
          "the JOIN FETCH on the active documents table must resolve the produced document under"
              + " the schedule tenant's v2 scope");
      assertEquals(documentId, terminal.getDocument().getId());
    }

    @Test
    @DisplayName("without a v2 scope the same query fail-closes the document, not the generation")
    void given_noPrimitiveScope_should_failCloseTheDocumentJoinButKeepTheGeneration() {
      // -- Act -- the identical query in a scope-less transaction (v1 TenantContext only)
      ReportingGeneration unscoped =
          rawTransaction()
              .execute(
                  status -> {
                    TenantContext.setCurrentTenant(tenantB);
                    try {
                      return reportingGenerationRepository
                          .findWithDocumentByIdAndTenantId(generationId, tenantB)
                          .orElse(null);
                    } finally {
                      TenantContext.clearCurrentTenant();
                    }
                  });

      // -- Assert -- reporting_generations is still v1, so the generation is found; documents is
      // active, so its JOIN FETCH fail-closes and the delivery would see no document. This is the
      // exact state the primitive scope fixes.
      assertNotNull(
          unscoped, "reporting_generations stays v1: the generation itself is still read");
      assertNull(
          unscoped.getDocument(),
          "documents is active: with no v2 scope the JOIN FETCH fail-closes to a null document");
    }
  }

  // -- SEEDING (auto-committed: this test is not @Transactional) --

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  /**
   * Seeds a SUCCESS generation of tenant B whose output is a documents row of the same tenant, in a
   * committed transaction. The document tenant is set explicitly (the persistence listener is gone
   * since documents went v2), so the INSERT carries the right tenant_id with no ambient scope.
   */
  private void seedSuccessfulGeneration(String tenantId) {
    rawTransaction()
        .execute(
            status -> {
              Reporting reporting = ReportingFixture.createDefaultReporting();
              reporting.setTenant(new Tenant(tenantId));
              entityManager.persist(reporting);

              Document document = new Document();
              document.setName("report-" + UUID.randomUUID() + ".pdf");
              document.setTarget(UUID.randomUUID() + ".pdf");
              document.setType(MediaType.APPLICATION_PDF_VALUE);
              document.setTenant(new Tenant(tenantId));
              entityManager.persist(document);

              ReportingGeneration generation = new ReportingGeneration();
              generation.setReporting(reporting);
              generation.setFormat(ReportingFormat.PDF);
              generation.setGenerationTrigger(ReportingGenerationTrigger.SCHEDULED);
              generation.setStatus(ReportingGenerationStatus.SUCCESS);
              generation.setCompletedAt(Instant.now());
              generation.setDocument(document);
              generation.setTenant(new Tenant(tenantId));
              entityManager.persist(generation);

              entityManager.flush();
              documentId = document.getId();
              generationId = generation.getId();
              return null;
            });
  }

  private TransactionTemplate rawTransaction() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    return template;
  }
}
