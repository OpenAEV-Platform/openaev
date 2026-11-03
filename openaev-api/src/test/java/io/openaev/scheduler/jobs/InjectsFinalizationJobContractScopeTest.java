package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code InjectsFinalizationJob} loads injects across tenants, and an inject loads its injector
 * contract eagerly. Once {@code injectors_contracts} is tenant-isolated, a sweep with no tenant
 * scope cannot load that contract, so the first phase throws and {@code execute} stops before the
 * auto-close and pending-inject phases, for every tenant.
 *
 * <p>The tests arm the production list plus {@code injectors_contracts}, and run the job on a bare
 * thread, as a Quartz worker does: no transaction, no session, no ambient tenant. Not
 * {@code @Transactional}: the scoped primitive refuses to open inside an active transaction, so
 * rows are committed through JDBC in a tenant of their own and removed after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("InjectsFinalizationJob with injector contracts tenant-isolated")
class InjectsFinalizationJobContractScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTablesWithInjectorContracts(DynamicPropertyRegistry registry) {
    TreeSet<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("injectors_contracts");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

  @Autowired private InjectsFinalizationJob injectsFinalizationJob;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private final String contractId = UUID.randomUUID().toString();

  @BeforeEach
  void seedATenantAndItsContract() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "finalization-contract-scope-" + tenantId);
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES (?, '{}', ?)",
        contractId,
        tenantId);
  }

  @AfterEach
  void sweep() {
    jdbc.update(
        "DELETE FROM execution_traces WHERE execution_inject_status_id IN (SELECT status_id FROM"
            + " injects_statuses WHERE status_inject IN (SELECT inject_id FROM injects WHERE"
            + " tenant_id = ?))",
        tenantId);
    jdbc.update(
        "DELETE FROM injects_statuses WHERE status_inject IN"
            + " (SELECT inject_id FROM injects WHERE tenant_id = ?)",
        tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injectors_contracts WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  private static void onBareThread(ThrowingRunnable work) throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                work.run();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the job failed", failure.get());
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private String seedRunningExercise() {
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, ?, 'RUNNING', ?, ?)",
        exerciseId,
        "finalization-contract-scope",
        "finalization@openaev.invalid",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        tenantId);
    return exerciseId;
  }

  private String seedInjectOnTheContract(String exerciseId, String collectStatus) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, inject_collect_status,"
            + " inject_injector_contract, tenant_id) VALUES (?, ?, false, true, 0, ?, ?, ?, ?)",
        injectId,
        "finalization-contract-scope",
        exerciseId,
        collectStatus,
        contractId,
        tenantId);
    return injectId;
  }

  private void seedStatus(String injectId, String statusName) {
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        injectId,
        statusName,
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
  }

  private String column(String sql, String id) {
    return jdbc.queryForObject(sql, String.class, id);
  }

  @Nested
  @DisplayName("execute")
  class Execute {

    @Test
    @DisplayName(
        "given a collecting inject and a pending inject on a contract of their tenant,"
            + " should run every phase")
    void given_collectingAndPendingInjectsOnATenantContract_should_runEveryPhase()
        throws Exception {
      // Arrange: a simulation whose only inject is done collecting, so auto-close finishes it,
      // and a second simulation with an inject stuck pending past the threshold
      String closingExerciseId = seedRunningExercise();
      String collectingInjectId = seedInjectOnTheContract(closingExerciseId, "COLLECTING");
      seedStatus(collectingInjectId, "EXECUTED");
      String pendingExerciseId = seedRunningExercise();
      String pendingInjectId = seedInjectOnTheContract(pendingExerciseId, null);
      seedStatus(pendingInjectId, "PENDING");

      // Act
      onBareThread(() -> injectsFinalizationJob.execute(null));

      // Assert: phase 1 completed the collection and kept the contract reference
      assertThat(
              column(
                  "SELECT inject_collect_status FROM injects WHERE inject_id = ?",
                  collectingInjectId))
          .isEqualTo("COMPLETED");
      assertThat(
              column(
                  "SELECT inject_injector_contract FROM injects WHERE inject_id = ?",
                  collectingInjectId))
          .isEqualTo(contractId);
      // Phase 2 ran after it: the simulation is closed
      assertThat(
              column(
                  "SELECT exercise_status FROM exercises WHERE exercise_id = ?", closingExerciseId))
          .isEqualTo("FINISHED");
      // Phase 3 ran after it: the pending inject is finalized
      assertThat(
              column(
                  "SELECT status_name FROM injects_statuses WHERE status_inject = ?",
                  pendingInjectId))
          .isNotEqualTo("PENDING");
    }
  }
}
