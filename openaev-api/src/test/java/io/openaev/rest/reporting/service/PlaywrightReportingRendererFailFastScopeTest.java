package io.openaev.rest.reporting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingFormat;
import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.ReportingGenerationStatus;
import io.openaev.database.model.Tenant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link PlaywrightReportingRenderer#render} writes its early-return failure row (no acting user,
 * or the acting user carries no API token) still inside the caller's own transaction, before the
 * v2 primitive is used at all. {@code reporting_generations} being tenant-active means that plain
 * save needs its own v2 scope on the current transaction, or the inspector fail-closes the UPDATE
 * (0 rows, no exception, no log): the caller's own scope is not always present on this path (a
 * plain {@code @Transactional} caller carries none by itself), so the write is nested through
 * {@code executeNew} with an explicit {@code TxCtx.forTenant}, never assumed from the ambient one.
 *
 * <p>The test opens the caller-side transaction through a bare {@link TransactionTemplate} with NO
 * scope set at all, the worst case a caller can hand this method, to prove the write survives on
 * its own rather than by inheriting a scope the test happened to set up already.
 *
 * <p>Not {@code @Transactional} at the class level: the executeNew nested transaction opens its own
 * physical connection and cannot see uncommitted rows on the test's connection. Seeds and cleans up
 * through plain JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=reporting_generations")
@WithMockUser(isAdmin = true)
@DisplayName("PlaywrightReportingRenderer.render fail-fast path under reporting_generations scope")
class PlaywrightReportingRendererFailFastScopeTest extends IntegrationTest {

  @Autowired private PlaywrightReportingRenderer renderer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  private String tenantId;
  private String reportingId;
  private String generationId;

  @BeforeEach
  void seedOneTenantWithOneReportingAndGeneration() throws Exception {
    tenantId =
        tenantHelper.createTenantWithCurrentUser("render-failfast-" + UUID.randomUUID()).getId();
    reportingId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, 'render-failfast', 'PLATFORM', ?)",
        reportingId,
        tenantId);
    generationId = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reporting_generations (reporting_generation_id, reporting_id,"
            + " reporting_generation_format, reporting_generation_status, tenant_id) VALUES (?, ?,"
            + " 'PDF', 'PENDING', ?)",
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

  @Test
  @DisplayName(
      "render with no acting user, called from a caller transaction with no scope of its own,"
          + " still persists ERROR")
  void given_noActingUserAndNoAmbientScope_should_persistErrorStatus() {
    // A detached, non-loaded generation, exactly as the real render() signature receives it: the
    // caller thread never re-fetches it through the scoped repository. Merging it in the nested
    // transaction overwrites reporting/tenant/format with these same values, matching the seed.
    ReportingGeneration generation = new ReportingGeneration();
    generation.setId(generationId);
    Reporting reporting = new Reporting();
    reporting.setId(reportingId);
    generation.setReporting(reporting);
    generation.setTenant(new Tenant(tenantId));
    generation.setFormat(ReportingFormat.PDF);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> renderer.render(generation, null));

    assertEquals(
        ReportingGenerationStatus.ERROR.name(),
        rawStatus(),
        "the fail-fast save must not fail closed once reporting_generations is tenant-active, even"
            + " when the caller transaction carries no scope of its own");
  }

  private String rawStatus() {
    return jdbcTemplate.queryForObject(
        "SELECT reporting_generation_status FROM reporting_generations WHERE"
            + " reporting_generation_id = ?",
        String.class,
        generationId);
  }
}
