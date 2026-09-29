package io.openaev.rest.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ReportingSchedule;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link ReportingScheduleLoader#loadEnabledSchedules} must see every tenant's enabled schedules,
 * not only one: it is the cross-tenant read the scheduling engine polls every minute. {@link
 * ReportingScheduleServiceTest} mocks this loader entirely and the render lifecycle tests only arm
 * {@code documents}, so nothing exercises the real {@code allTenants()} read against the real
 * {@code reporting_schedules}/{@code reportings} tables. An omitted {@code allTenants()} scope here
 * would silently load zero schedules with no exception and no log.
 *
 * <p>Not {@code @Transactional}: {@link io.openaev.context.TenantScopedTransaction#execute} opens
 * its own transaction and refuses to run inside one already active. Seeds and cleans up through
 * plain JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=reporting_schedules,reportings")
@WithMockUser(isAdmin = true)
@DisplayName("ReportingScheduleLoader loads enabled schedules across every tenant")
class ReportingScheduleLoaderTenantScopeTest extends IntegrationTest {

  @Autowired private ReportingScheduleLoader loader;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantA;
  private String tenantB;
  private String reportingA;
  private String reportingB;
  private String scheduleA;
  private String scheduleB;
  private String ownerId;

  @BeforeEach
  void seedTwoTenantsWithOneEnabledScheduleEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("schedule-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("schedule-scope-b").getId();
    ownerId = testUserHolder.get().getId();
    reportingA = seedReporting(tenantA, "schedule-scope-reporting-a");
    reportingB = seedReporting(tenantB, "schedule-scope-reporting-b");
    scheduleA = seedEnabledSchedule(tenantA, reportingA);
    scheduleB = seedEnabledSchedule(tenantB, reportingB);
  }

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update(
        "DELETE FROM reporting_schedules WHERE reporting_schedule_id IN (?, ?)",
        scheduleA,
        scheduleB);
    jdbcTemplate.update("DELETE FROM reportings WHERE reporting_id IN (?, ?)", reportingA, reportingB);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Test
  @DisplayName("loadEnabledSchedules returns both tenants' schedules, not only one")
  void given_enabledSchedulesInTwoTenants_should_loadBoth() {
    List<ReportingSchedule> loaded = loader.loadEnabledSchedules();

    assertThat(loaded.stream().map(ReportingSchedule::getId))
        .as("both tenants' enabled schedules must be loaded, not silently dropped to zero")
        .contains(scheduleA, scheduleB);
  }

  private String seedReporting(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reportings (reporting_id, reporting_name, reporting_context_type, tenant_id)"
            + " VALUES (?, ?, 'PLATFORM', ?)",
        id,
        name,
        tenantId);
    return id;
  }

  private String seedEnabledSchedule(String tenantId, String reportingId) {
    String id = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "INSERT INTO reporting_schedules (reporting_schedule_id, reporting_id,"
            + " reporting_schedule_period, reporting_schedule_time, reporting_schedule_format,"
            + " reporting_schedule_enabled, user_id, tenant_id) VALUES (?, ?, 'DAY', '09:00',"
            + " 'PDF', true, ?, ?)",
        id,
        reportingId,
        ownerId,
        tenantId);
    return id;
  }
}
