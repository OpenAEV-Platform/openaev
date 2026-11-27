package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The scheduled purge must actually delete an expired tenant, and one tenant that cannot be purged
 * must not keep the others alive.
 *
 * <p>The tenants of the first group are onboarded through {@link TenantService#create} and soft
 * deleted through {@link TenantService#softDelete}, as production does: onboarding seeds rows (for
 * example {@code collector_types}) that a tenant built by hand does not have, and those rows are
 * what the tenant delete has to get through. Only the deletion date is moved back, past the grace
 * period.
 *
 * <p>NOT {@code @Transactional}: the purge commits each tenant on its own, and a surrounding test
 * transaction would hide the rows it is meant to delete.
 */
@WithMockUser(isAdmin = true)
@Import(TenantPurgeTest.FailingManagerConfiguration.class)
@DisplayName("Tenant purge")
class TenantPurgeTest extends IntegrationTest {

  enum Failure {
    /** The cleanup throws an unchecked exception. */
    UNCHECKED_EXCEPTION,
    /** The cleanup runs a statement the database refuses, which aborts the transaction. */
    ABORTED_TRANSACTION
  }

  /**
   * A {@link DependenciesManager} that fails the cleanup of the tenants it is told to. {@link
   * TenantService} injects every manager bean in the context, so the failure happens inside the
   * real purge loop.
   */
  static class FailingManager implements DependenciesManager {

    private final EntityManager entityManager;
    final Map<String, Failure> failures = new ConcurrentHashMap<>();

    FailingManager(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Override
    public void createDependencyForTenant(Tenant tenant) {
      // Nothing to provision: this manager only fails the delete side.
    }

    @Override
    public void deleteDependencyForTenant(String tenantId) {
      Failure failure = failures.get(tenantId);
      if (failure == Failure.UNCHECKED_EXCEPTION) {
        throw new IllegalStateException("cleanup failure for tenant " + tenantId);
      }
      if (failure == Failure.ABORTED_TRANSACTION) {
        entityManager.createNativeQuery("SELECT 1 / 0").getSingleResult();
      }
    }
  }

  @TestConfiguration
  static class FailingManagerConfiguration {
    @Bean
    FailingManager failingManager(EntityManager entityManager) {
      return new FailingManager(entityManager);
    }
  }

  @Autowired private TenantService tenantService;
  @Autowired private TenantQueueService tenantQueueService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private FailingManager failingManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private final List<String> tenantIds = new ArrayList<>();
  private ListAppender<ILoggingEvent> appender;
  private Logger serviceLogger;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    failingManager.failures.clear();
    serviceLogger = (Logger) LoggerFactory.getLogger(TenantService.class);
    appender = new ListAppender<>();
    appender.start();
    serviceLogger.addAppender(appender);
  }

  @AfterEach
  void cleanup() throws Exception {
    serviceLogger.detachAppender(appender);
    appender.stop();
    failingManager.failures.clear();
    for (String tenantId : tenantIds) {
      if (countRows("tenants", tenantId) == 0) {
        continue;
      }
      tenantQueueService.deleteDependencyForTenant(tenantId);
      jdbc.update(
          "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN"
              + " (SELECT scenario_id FROM scenarios WHERE tenant_id = ?)",
          tenantId);
      tenantHelper.deleteCommittedTenants(tenantId);
    }
    tenantIds.clear();
  }

  private String onboardExpiredTenant(String name) throws Exception {
    String tenantId = tenantHelper.createTenant(name).getId();
    tenantIds.add(tenantId);
    tenantService.softDelete(tenantId);
    expire(tenantId);
    return tenantId;
  }

  /** Raw JDBC: a tenant with no onboarded rows, for the tests about the loop itself. */
  private String seedExpiredTenant(String name) {
    String tenantId = UUID.randomUUID().toString();
    tenantIds.add(tenantId);
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at,"
            + " tenant_deleted_at) VALUES (?, ?, now(), now(), now())",
        tenantId,
        name + "-" + tenantId.substring(0, 8));
    expire(tenantId);
    return tenantId;
  }

  private void expire(String tenantId) {
    jdbc.update(
        "UPDATE tenants SET tenant_deleted_at = now() - interval '"
            + (TenantService.SOFT_DELETE_RETENTION_DAYS + 1)
            + " days' WHERE tenant_id = ?",
        tenantId);
  }

  private int countRows(String table, String tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenantId);
  }

  @Nested
  @DisplayName("a tenant onboarded through the production path")
  class OnboardedTenant {

    @Test
    @DisplayName("given an expired onboarded tenant, should delete it and every row it owns")
    void given_anExpiredOnboardedTenant_should_deleteItAndEveryRowItOwns() throws Exception {
      // Arrange
      String tenantId = onboardExpiredTenant("purge-onboarded");
      assertThat(countRows("collector_types", tenantId))
          .as("onboarding seeds collector types, which is what the tenant delete must get through")
          .isPositive();
      assertThat(countRows("domains", tenantId)).isPositive();

      // Act
      tenantService.purgeExpiredTenants();

      // Assert
      assertThat(countRows("tenants", tenantId)).as("the tenant row").isZero();
      assertThat(countRows("collector_types", tenantId)).isZero();
      assertThat(countRows("collectors", tenantId)).isZero();
      assertThat(countRows("domains", tenantId)).isZero();
      assertThat(countRows("roles", tenantId)).isZero();
      assertThat(countRows("groups", tenantId)).isZero();
    }

    @Test
    @DisplayName(
        "given an expired onboarded tenant with a scenario carrying a reply-to address, should"
            + " delete it")
    void given_anExpiredOnboardedTenantWithAScenarioReplyTo_should_deleteIt() throws Exception {
      // Arrange - every scenario created through the service gets the default reply-to address.
      String tenantId = onboardExpiredTenant("purge-reply-to");
      String scenarioId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id)"
              + " VALUES (?, 'purge-reply-to', ?)",
          scenarioId,
          tenantId);
      jdbc.update(
          "INSERT INTO scenario_mails_reply_to (scenario_id, scenario_reply_to)"
              + " VALUES (?, 'reply@openaev.io')",
          scenarioId);

      // Act
      tenantService.purgeExpiredTenants();

      // Assert
      assertThat(countRows("tenants", tenantId)).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM scenario_mails_reply_to WHERE scenario_id = ?",
                  Integer.class,
                  scenarioId))
          .isZero();
    }

    @Test
    @DisplayName("given a tenant soft deleted within its grace period, should keep it")
    void given_aTenantSoftDeletedWithinItsGracePeriod_should_keepIt() throws Exception {
      // Arrange
      String expiredTenantId = onboardExpiredTenant("purge-expired");
      String recentTenantId = tenantHelper.createTenant("purge-recent").getId();
      tenantIds.add(recentTenantId);
      tenantService.softDelete(recentTenantId);

      // Act
      tenantService.purgeExpiredTenants();

      // Assert
      assertThat(countRows("tenants", expiredTenantId)).isZero();
      assertThat(countRows("tenants", recentTenantId)).isEqualTo(1);
      assertThat(countRows("collector_types", recentTenantId)).isPositive();
    }
  }

  @Nested
  @DisplayName("one tenant that cannot be purged")
  class FailureIsolation {

    @Test
    @DisplayName(
        "given a batch where one tenant's cleanup throws, should purge the others and report the"
            + " failed one")
    void given_aBatchWhereOneTenantsCleanupThrows_should_purgeTheOthersAndReportTheFailedOne() {
      assertOtherTenantsArePurged(Failure.UNCHECKED_EXCEPTION);
    }

    @Test
    @DisplayName(
        "given a batch where one tenant's cleanup aborts its transaction, should purge the others"
            + " and report the failed one")
    void given_aBatchWhereOneTenantsCleanupAbortsItsTransaction_should_purgeTheOthers() {
      assertOtherTenantsArePurged(Failure.ABORTED_TRANSACTION);
    }

    private void assertOtherTenantsArePurged(Failure failure) {
      // Arrange
      String firstTenantId = seedExpiredTenant("purge-first");
      String failingTenantId = seedExpiredTenant("purge-failing");
      String lastTenantId = seedExpiredTenant("purge-last");
      failingManager.failures.put(failingTenantId, failure);

      // Act
      Throwable thrown = catchThrowable(() -> tenantService.purgeExpiredTenants());

      // Assert
      assertOnlyTheFailedTenantIsKept(firstTenantId, failingTenantId, lastTenantId, thrown);
    }
  }

  /**
   * The tenant row delete itself is refused, as a foreign key without a delete action refuses it:
   * the cleanup succeeds and the failure only comes with the delete.
   */
  @Nested
  @DisplayName("one tenant whose row delete is refused")
  class RefusedTenantDelete {

    private static final String REFUSED_NAME = "purge-refused-delete";

    @BeforeEach
    void refuseTheDeleteOfMarkedTenants() {
      jdbc.execute(
          "CREATE OR REPLACE FUNCTION purge_test_refuse_tenant_delete() RETURNS trigger"
              + " LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION USING"
              + " ERRCODE = 'foreign_key_violation', MESSAGE = 'tenant row still referenced: '"
              + " || OLD.tenant_id; END $$");
      jdbc.execute(
          "CREATE OR REPLACE TRIGGER purge_test_refuse_tenant_delete BEFORE DELETE ON tenants"
              + " FOR EACH ROW WHEN (OLD.tenant_name LIKE '"
              + REFUSED_NAME
              + "-%') EXECUTE FUNCTION purge_test_refuse_tenant_delete()");
    }

    @AfterEach
    void acceptTheDeleteAgain() {
      jdbc.execute("DROP TRIGGER IF EXISTS purge_test_refuse_tenant_delete ON tenants");
      jdbc.execute("DROP FUNCTION IF EXISTS purge_test_refuse_tenant_delete()");
    }

    @Test
    @DisplayName(
        "given a batch where one tenant row delete is refused, should purge the others and report"
            + " the refused one")
    void given_aBatchWhereOneTenantRowDeleteIsRefused_should_purgeTheOthersAndReportIt() {
      // Arrange
      String firstTenantId = seedExpiredTenant("purge-first");
      String refusedTenantId = seedExpiredTenant(REFUSED_NAME);
      String lastTenantId = seedExpiredTenant("purge-last");

      // Act
      Throwable thrown = catchThrowable(() -> tenantService.purgeExpiredTenants());

      // Assert
      assertOnlyTheFailedTenantIsKept(firstTenantId, refusedTenantId, lastTenantId, thrown);
    }
  }

  private void assertOnlyTheFailedTenantIsKept(
      String firstTenantId, String failedTenantId, String lastTenantId, Throwable thrown) {
    // The others are gone, the failed one stays soft deleted for the next run.
    assertThat(countRows("tenants", firstTenantId)).isZero();
    assertThat(countRows("tenants", lastTenantId)).isZero();
    assertThat(countRows("tenants", failedTenantId)).isEqualTo(1);

    // The run reports the failure instead of skipping it silently.
    assertThat(thrown)
        .as("the run must end in error, naming the failed tenant")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(failedTenantId);
    assertThat(appender.list)
        .filteredOn(event -> event.getLevel() == Level.ERROR)
        .anySatisfy(
            event -> {
              assertThat(event.getFormattedMessage()).contains(failedTenantId);
              assertThat(event.getThrowableProxy()).isNotNull();
            });
  }
}
