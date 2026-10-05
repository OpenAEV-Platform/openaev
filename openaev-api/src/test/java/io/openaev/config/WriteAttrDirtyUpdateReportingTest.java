package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Reporting;
import io.openaev.database.model.ReportingContextType;
import io.openaev.database.model.ReportingSchedule;
import io.openaev.database.model.ReportingSchedulePeriod;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ReportingScheduleRepository;
import io.openaev.rest.reporting.service.ReportingScheduleLoader;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * A managed entity mutated through dirty checking is never handed to {@code persist} or {@code
 * merge}. Two things keep such a write out of silence. The frame that loaded the entity is captured
 * at load time, so a production method that reloads a row and sets a column is attributed to that
 * method even though the UPDATE flushes later from a test frame. And when nobody captured the
 * entity at all (it was loaded before the recording window opened), the flush-time write with no
 * production frame on its stack is reported as unattributed and keyed on the test that flushed it,
 * so it is visible and triageable instead of being waived as test-driven.
 *
 * <p>The production shape is {@code ReportingScheduleLoader.markLastRun}: a managed reload by id
 * and a setter, no save.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution: a dirty-checked update is attributed to its loader, or reported")
class WriteAttrDirtyUpdateReportingTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TABLE = "reporting_schedules";
  private static final String LOADER =
      "io.openaev.rest.reporting.service.ReportingScheduleLoader.markLastRun";
  private static final String UNATTRIBUTED_KEY =
      "unattributed(io.openaev.config.WriteAttrDirtyUpdateReportingTest$DirtyUpdate"
          + ".given_entityLoadedBeforeRecording_should_beReportedUnattributed)";

  @Autowired private ReportingScheduleLoader reportingScheduleLoader;
  @Autowired private ReportingScheduleRepository reportingScheduleRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String tenantB;
  private String scheduleId;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-dirty-b").getId();
    // Seed a schedule in the default tenant while no scope is set (the trigger stays silent).
    Tenant defaultTenant = entityManager.getReference(Tenant.class, DEFAULT_TENANT);
    Reporting reporting = new Reporting();
    reporting.setName("wattr reporting");
    reporting.setContextType(ReportingContextType.PLATFORM);
    reporting.setTenant(defaultTenant);
    entityManager.persist(reporting);
    ReportingSchedule schedule = new ReportingSchedule();
    schedule.setReporting(reporting);
    schedule.setName("wattr schedule");
    schedule.setPeriod(ReportingSchedulePeriod.DAY);
    schedule.setTriggerTime("09:00");
    schedule.setOwner(testUserHolder.get());
    schedule.setTenant(defaultTenant);
    entityManager.persist(schedule);
    entityManager.flush();
    entityManager.clear();
    scheduleId = schedule.getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Nested
  @DisplayName("Given a production method that mutates a managed entity without saving it")
  class DirtyUpdate {

    @Test
    @DisplayName(
        "Given the method loaded the entity itself, should attribute the update to the method")
    void given_methodLoadedTheEntity_should_attributeToTheMethod() {
      // Arrange
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);

      // Act: the loader reloads the schedule (default tenant) and sets a column; nothing is saved.
      reportingScheduleLoader.markLastRun(scheduleId, Instant.now());
      String writtenTenant = readTenantFromTestFrame();
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(DEFAULT_TENANT, writtenTenant, "precondition: the row is in the default tenant");
      assertFalse(scheduleViolations().isEmpty(), "the trigger must see the dirty update");
      List<String> attributed = attributed();
      assertTrue(
          attributed.contains(TABLE + " DEFAULT " + LOADER),
          "a dirty update must be attributed to the production frame that loaded the entity;"
              + " attributed="
              + attributed
              + " violations="
              + scheduleViolations());
    }

    @Test
    @DisplayName("Given the entity was loaded before recording, should be reported as unattributed")
    void given_entityLoadedBeforeRecording_should_beReportedUnattributed() {
      // Arrange: the schedule enters the persistence context outside the recording window, so no
      // frame is captured for it; the loader's reload is then a cache hit that raises no load
      // event.
      TenantContext.clearCurrentTenant();
      WriteAttrDetectorRecorder.stop();
      reportingScheduleRepository.findById(scheduleId).orElseThrow();
      WriteAttrDetectorRecorder.start();
      setScope(tenantB);

      // Act
      reportingScheduleLoader.markLastRun(scheduleId, Instant.now());
      String writtenTenant = readTenantFromTestFrame();
      WriteAttrDetectorRecorder.stop();

      // Assert
      assertEquals(DEFAULT_TENANT, writtenTenant, "precondition: the row is in the default tenant");
      assertFalse(scheduleViolations().isEmpty(), "the trigger must see the dirty update");
      List<String> attributed = attributed();
      assertTrue(
          attributed.contains(TABLE + " DEFAULT " + UNATTRIBUTED_KEY),
          "a flush-time write nobody captured must be reported as unattributed, keyed on the"
              + " flushing test, not waived; attributed="
              + attributed
              + " violations="
              + scheduleViolations());
    }
  }

  private List<Violation> scheduleViolations() {
    return WriteAttrDetectorRecorder.violations().stream()
        .filter(v -> TABLE.equals(v.table()))
        .toList();
  }

  private List<String> attributed() {
    return WriteAttrGateExtension.offendingSignatures(
        WriteAttrDetectorRecorder.violations(), Set.of());
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  /**
   * Forces the pending flush from this test method with a native query the inspector leaves alone
   * (no table), so the flush stack holds no production frame, then reads the row through the
   * transaction's JDBC connection, which the statement inspector does not rewrite: under the
   * production active-tables list a scoped native read of an active table would not see a row of
   * another tenant, which is exactly the row this test wrote on purpose.
   */
  private String readTenantFromTestFrame() {
    entityManager.createNativeQuery("SELECT 1").getSingleResult();
    return jdbcTemplate.queryForObject(
        "SELECT tenant_id FROM reporting_schedules WHERE reporting_schedule_id = ?",
        String.class,
        scheduleId);
  }
}
