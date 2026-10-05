package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Pins that {@code reporting_generations} being tenant-active does not silently stop the render
 * lifecycle from updating a generation's status. {@link PlaywrightReportingRenderer#markRunning},
 * {@link PlaywrightReportingRenderer#completeWithSuccess} and {@link
 * PlaywrightReportingRenderer#completeWithError} each read the generation with an explicit {@code
 * findByIdAndTenantId} predicate, correct on its own, but the statement inspector ALSO requires the
 * transaction's {@code TxCtx} scope: on the render thread, which runs outside any request or
 * {@code @Transactional} entrypoint, nothing sets that scope unless the method wraps its read/write
 * in {@link io.openaev.scheduler.TenantScopedJobRunner}. Before that wrap, the read fails closed,
 * {@code ifPresent} never fires, and the generation is stuck PENDING forever with no log and no
 * exception.
 *
 * <p>Not {@code @Transactional}: {@code TenantScopedJobRunner} opens its own transaction through
 * {@link io.openaev.context.TenantScopedTransaction#execute}, which refuses to run inside an
 * already-active one. Rows are seeded and cleaned up with plain JDBC, following the established
 * pattern for background-primitive tests (see {@code SecurityCoverageTenantScopeTest}).
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=reporting_generations")
@WithMockUser(isAdmin = true)
@DisplayName("reporting_generations render lifecycle tenant scope")
class PlaywrightReportingRendererTenantScopeTest extends IntegrationTest {

  @Autowired private PlaywrightReportingRenderer renderer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantId;
  private String reportingId;
  private String generationId;

  @BeforeEach
  void seedOneTenantWithOneReportingAndGeneration() throws Exception {
    tenantId =
        tenantHelper.createTenantWithCurrentUser("render-scope-" + UUID.randomUUID()).getId();
    reportingId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, 'render-scope', 'PLATFORM', ?)",
        reportingId,
        tenantId);
    generationId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
            + " reporting_generation_format, tenant_id) VALUES (?, ?, 'PDF', ?)",
        generationId,
        reportingId,
        tenantId);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM reporting_generations WHERE reporting_generation_id = ?", generationId);
    jdbcTemplate.update("DELETE FROM reportings WHERE reporting_id = ?", reportingId);
    tenantHelper.deleteCommittedTenants(tenantId);
  }

  private PlaywrightReportingRenderer.RenderJob job() {
    return new PlaywrightReportingRenderer.RenderJob(
        generationId, reportingId, "render-scope", ReportingFormat.PDF, tenantId, "token");
  }

  @Test
  @DisplayName("markRunning flips the generation to RUNNING under the job's own tenant scope")
  void given_activeTable_should_markRunningUpdateStatus() {
    renderer.markRunning(job());
    assertEquals(
        ReportingGenerationStatus.RUNNING.name(), rawStatus(), "markRunning must not fail closed");
  }

  @Test
  @DisplayName(
      "completeWithSuccess flips the generation to SUCCESS under the job's own tenant scope")
  void given_activeTable_should_completeWithSuccessUpdateStatus() {
    renderer.completeWithSuccess(job(), null);
    assertEquals(
        ReportingGenerationStatus.SUCCESS.name(),
        rawStatus(),
        "completeWithSuccess must not fail closed");
  }

  @Test
  @DisplayName("completeWithError flips the generation to ERROR under the job's own tenant scope")
  void given_activeTable_should_completeWithErrorUpdateStatus() {
    renderer.completeWithError(job(), "boom");
    assertEquals(
        ReportingGenerationStatus.ERROR.name(),
        rawStatus(),
        "completeWithError must not fail closed");
  }

  private String rawStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT reporting_generation_status FROM reporting_generations WHERE"
            + " reporting_generation_id = ?",
        String.class,
        generationId);
  }
}
