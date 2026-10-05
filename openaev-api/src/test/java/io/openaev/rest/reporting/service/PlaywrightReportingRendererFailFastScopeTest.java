package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ReportingGenerationRepository;
import io.openaev.database.repository.ReportingRepository;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link PlaywrightReportingRenderer#render} writes its early-return failure row (no acting user,
 * or the acting user carries no API token) synchronously, on the caller thread, before any
 * background work is dispatched. Both callers reach it one statement after creating the generation
 * row in their own transaction ({@code ReportingService.doRequestGeneration} saves the PENDING
 * generation, then calls {@code render} with it), so the row the status update targets is still
 * uncommitted.
 *
 * <p>That is why the write joins the caller's transaction, scoped through {@link
 * TenantScopedJobRunner#runInCurrentTenantTransaction} because {@code reporting_generations} is
 * tenant-active, instead of nesting a REQUIRES_NEW one. A nested transaction reads its own snapshot
 * and cannot see the uncommitted row: {@code save} resolves to {@code merge}, the select finds
 * nothing, and Hibernate rejects the entity with a {@code StaleObjectStateException} that takes the
 * caller's whole unit of work down with it.
 *
 * <p>Each test therefore saves the generation through the repository inside the caller transaction,
 * exactly as production does, rather than seeding a committed row: a pre-committed row makes the
 * merge find its target and hides the defect entirely.
 *
 * <p>Not {@code @Transactional} at the class level: the tests own their caller transaction, and the
 * primitive refuses to open inside an active one. Seeds and cleans up through plain JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=reporting_generations")
@WithMockUser(isAdmin = true)
@DisplayName("PlaywrightReportingRenderer.render fail-fast path under reporting_generations scope")
class PlaywrightReportingRendererFailFastScopeTest extends IntegrationTest {

  @Autowired private PlaywrightReportingRenderer renderer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ReportingRepository reportingRepository;
  @Autowired private ReportingGenerationRepository reportingGenerationRepository;
  @Autowired private EntityManager entityManager;

  private String tenantId;
  private String reportingId;

  @BeforeEach
  void seedOneTenantWithOneReporting() throws Exception {
    tenantId =
        tenantHelper.createTenantWithCurrentUser("render-failfast-" + UUID.randomUUID()).getId();
    reportingId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, 'render-failfast', 'PLATFORM', ?)",
        reportingId,
        tenantId);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM reporting_generations WHERE reporting_id = ?", reportingId);
    jdbcTemplate.update("DELETE FROM reportings WHERE reporting_id = ?", reportingId);
    tenantHelper.deleteCommittedTenants(tenantId);
  }

  @Nested
  @DisplayName("when the caller created the generation in its own, still uncommitted transaction")
  class WhenTheGenerationRowIsStillUncommitted {

    @Test
    @DisplayName("an HTTP-shaped caller transaction commits exactly one generation in ERROR")
    void given_httpShapedCallerTransaction_should_persistErrorOnTheCallerRow() {
      // ARRANGE: an ambient transaction already carrying the request's tenant scope, which is what
      // the transaction aspect leaves behind on an endpoint taking a TxCtx (ReportingApi
      // .generateReporting). The caller has no acting user, so render takes its fail-fast branch.

      // ACT
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
                renderer.render(saveNewPendingGeneration(), null);
              });

      // ASSERT
      assertEquals(
          1,
          generationCount(),
          "the fail-fast save must update the caller's row, not insert a second one");
      assertEquals(
          ReportingGenerationStatus.ERROR.name(),
          committedStatus(),
          "the fail-fast status must be committed with the caller's own transaction");
    }

    @Test
    @DisplayName(
        "the schedule engine's primitive transaction commits exactly one generation in ERROR")
    void given_scheduleEnginePrimitiveTransaction_should_persistErrorOnTheCallerRow() {
      // ARRANGE: mirrors ReportingScheduleService.executeSchedule, which calls requestGeneration
      // (and therefore render) from inside its own TenantScopedJobRunner transaction. Setting the
      // scope on that transaction must be accepted, not refused as a scope change.

      // ACT
      tenantScopedJobRunner.runInTenant(
          tenantId, () -> renderer.render(saveNewPendingGeneration(), null));

      // ASSERT
      assertEquals(
          1,
          generationCount(),
          "the fail-fast save must update the caller's row, not insert a second one");
      assertEquals(
          ReportingGenerationStatus.ERROR.name(),
          committedStatus(),
          "the fail-fast status must be committed with the schedule engine's own transaction");
    }
  }

  @Nested
  @DisplayName("when the caller transaction carries no scope of its own")
  class WhenTheCallerTransactionHasNoScope {

    @Test
    @DisplayName("render scopes that transaction to the generation's own tenant and still commits")
    void given_callerTransactionWithoutScope_should_scopeItToTheGenerationTenant() {
      // ARRANGE: the worst case a caller can hand this method, a plain transaction with nothing
      // set. The commit below only succeeds because render sets the scope: the flush writes the
      // row as an insert plus an update of the fields set afterwards, and that update is the
      // statement the inspector filters.
      AtomicReference<String> scopeAfterRender = new AtomicReference<>();

      // ACT
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                renderer.render(saveNewPendingGeneration(), null);
                scopeAfterRender.set(currentTenantScope());
              });

      // ASSERT
      assertEquals(
          tenantId,
          scopeAfterRender.get(),
          "render must scope the caller transaction to the generation's own tenant, never leave it"
              + " unscoped and never substitute another tenant");
      assertEquals(
          ReportingGenerationStatus.ERROR.name(),
          committedStatus(),
          "the fail-fast status must be committed even when the caller carries no scope");
    }
  }

  /** Same statement as ReportingService.doRequestGeneration: a PENDING row, not yet committed. */
  private ReportingGeneration saveNewPendingGeneration() {
    ReportingGeneration generation = new ReportingGeneration();
    Reporting reporting = reportingRepository.findById(reportingId).orElseThrow();
    generation.setReporting(reporting);
    generation.setTenant(new Tenant(tenantId));
    generation.setFormat(ReportingFormat.PDF);
    generation.setStatus(ReportingGenerationStatus.PENDING);
    return reportingGenerationRepository.save(generation);
  }

  /** The transaction-local scope the inspector reads, on the caller's own transaction. */
  private String currentTenantScope() {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.current_tenants', true), '')")
            .getSingleResult();
  }

  private int generationCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM reporting_generations WHERE reporting_id = ?",
        Integer.class,
        reportingId);
  }

  private String committedStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT reporting_generation_status FROM reporting_generations WHERE reporting_id = ?",
        String.class,
        reportingId);
  }
}
