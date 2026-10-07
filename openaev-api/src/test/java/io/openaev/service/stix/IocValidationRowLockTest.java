package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationStatus;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Every write of an existing validation loads its row with the row lock. The entity is saved with
 * all its columns: a write that read the row without the lock while another transaction held it
 * would, once that transaction commits, put its stale snapshot over the committed state. Each test
 * changes the row in a transaction that holds the lock, starts the write under test in another one,
 * waits until PostgreSQL reports that write waiting for a lock, then commits the first.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=ioc_validations")
@WithMockUser(isAdmin = true)
@DisplayName("IOC validation row lock")
class IocValidationRowLockTest extends IntegrationTest {

  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired private IocValidationService iocValidationService;
  @Autowired private IocValidationRepository iocValidationRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbc;

  private String tenantId;
  private ExecutorService pool;

  @BeforeEach
  void setUp() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("ioc-validation-row-lock").getId();
    pool = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void cleanUp() throws Exception {
    pool.shutdownNow();
    pool.awaitTermination(WAIT.toSeconds(), TimeUnit.SECONDS);
    jdbc.update("DELETE FROM ioc_validations WHERE tenant_id = ?", tenantId);
    tenantHelper.deleteCommittedTenants(tenantId);
  }

  @Test
  @DisplayName("an acknowledged status recorded during an approval keeps the approval")
  void given_approvalHoldingTheRow_should_keepItWhenTheAcknowledgementIsRecorded()
      throws Exception {
    String id = insertValidation(IocValidationStatus.AWAITING_APPROVAL);

    whileLocked(
        id,
        validation -> {
          validation.setStatus(IocValidationStatus.RUNNING);
          validation.setStatusMessage("Approved");
        },
        () -> iocValidationService.markSynced(tenantId, id, IocValidationStatus.AWAITING_APPROVAL));

    Map<String, Object> row = row(id);
    assertThat(row.get("ioc_validation_status")).isEqualTo("RUNNING");
    assertThat(row.get("ioc_validation_status_message")).isEqualTo("Approved");
    assertThat(row.get("ioc_validation_opencti_synced_status")).isEqualTo("AWAITING_APPROVAL");
  }

  @Test
  @DisplayName("results recorded as pushed while a status is acknowledged keep the acknowledgement")
  void given_acknowledgementHoldingTheRow_should_keepItWhenTheResultsArePushed() throws Exception {
    String id = insertValidation(IocValidationStatus.COMPLETED);

    whileLocked(
        id,
        validation -> validation.setLifecycleSyncedStatus(IocValidationStatus.COMPLETED),
        () -> iocValidationService.markResultsPushed(tenantId, id, Instant.now()));

    Map<String, Object> row = row(id);
    assertThat(row.get("ioc_validation_opencti_synced_status")).isEqualTo("COMPLETED");
    assertThat(row.get("ioc_validation_results_pushed_at")).isNotNull();
  }

  @Test
  @DisplayName("results computed while a status is acknowledged keep the acknowledgement")
  void given_acknowledgementHoldingTheRow_should_keepItWhenTheResultsAreComputed()
      throws Exception {
    String id = insertValidation(IocValidationStatus.RUNNING);

    // No pair and no simulation: the computation closes the validation
    whileLocked(
        id,
        validation -> validation.setLifecycleSyncedStatus(IocValidationStatus.RUNNING),
        () -> inTenant(() -> iocValidationService.computeResults(id, Instant.now())));

    Map<String, Object> row = row(id);
    assertThat(row.get("ioc_validation_status")).isEqualTo("FAILED");
    assertThat(row.get("ioc_validation_completed_at")).isNotNull();
    assertThat(row.get("ioc_validation_opencti_synced_status")).isEqualTo("RUNNING");
  }

  /**
   * Applies {@code change} in a transaction holding the row lock, runs {@code write} in another
   * thread, and commits the change only once the write waits for the lock.
   */
  private void whileLocked(String id, Consumer<IocValidation> change, Runnable write)
      throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Future<?> holder =
        pool.submit(
            () ->
                inTenant(
                    () -> {
                      IocValidation validation =
                          iocValidationRepository.findByIdForUpdate(id).orElseThrow();
                      change.accept(validation);
                      iocValidationRepository.saveAndFlush(validation);
                      locked.countDown();
                      awaitQuietly(release);
                    }));
    try {
      assertThat(locked.await(WAIT.toSeconds(), TimeUnit.SECONDS))
          .as("the first transaction holds the row lock")
          .isTrue();
      Future<?> writer = pool.submit(write);
      assertThat(awaitLockWait()).as("the write waits for the row lock").isTrue();
      release.countDown();
      holder.get(WAIT.toSeconds(), TimeUnit.SECONDS);
      writer.get(WAIT.toSeconds(), TimeUnit.SECONDS);
    } finally {
      release.countDown();
    }
  }

  /** Whether a session waits for a lock on the validations table within {@link #WAIT}. */
  private boolean awaitLockWait() throws InterruptedException {
    Instant deadline = Instant.now().plus(WAIT);
    while (Instant.now().isBefore(deadline)) {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                  + " AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'"
                  + " AND query ILIKE '%ioc_validations%'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return true;
      }
      Thread.sleep(20);
    }
    return false;
  }

  private void inTenant(Runnable work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(WAIT.toSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private Map<String, Object> row(String id) {
    return jdbc.queryForMap(
        "SELECT ioc_validation_status, ioc_validation_status_message,"
            + " ioc_validation_opencti_synced_status, ioc_validation_results_pushed_at,"
            + " ioc_validation_completed_at FROM ioc_validations WHERE ioc_validation_id = ?",
        id);
  }

  private String insertValidation(IocValidationStatus status) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO ioc_validations (ioc_validation_id, ioc_validation_external_id,"
            + " ioc_validation_name, ioc_validation_status, tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        UUID.randomUUID().toString(),
        "Row lock " + status,
        status.name(),
        tenantId);
    return id;
  }
}
