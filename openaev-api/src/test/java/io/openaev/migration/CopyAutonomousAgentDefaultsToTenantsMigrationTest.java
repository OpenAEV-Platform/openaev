package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.util.List;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies, on the live schema, that the platform rows of the autonomous run's default additional
 * agent settings are copied to every tenant and then removed, so the tenant-scoped read finds them.
 *
 * <p>{@code @Transactional} so the rows seeded here roll back with the test transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261106110000000: autonomous agent defaults copied to every tenant")
class CopyAutonomousAgentDefaultsToTenantsMigrationTest extends IntegrationTest {

  private static final String AGENTS_KEY = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENTS.key();
  private static final String MODES_KEY = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENT_MODES.key();
  private static final String PLATFORM_AGENTS = "[\"platform-agent\"]";
  private static final String PLATFORM_MODES = "{\"platform-agent\":\"FAST\"}";
  private static final String TENANT_OWN_AGENTS = "[\"own-agent\"]";

  @Autowired private V6_20261106110000000__Copy_autonomous_agent_defaults_to_tenants migration;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @Nested
  @DisplayName("Upgrade of a platform row")
  class UpgradeOfAPlatformRow {

    @Test
    @DisplayName("Every existing tenant reads the value the platform row held")
    void given_platformRowOnly_should_giveEveryTenantItsOwnCopy() throws Exception {
      // Arrange
      Tenant other = tenantHelper.createTenantWithCurrentUser("autonomous-defaults-other");
      seed(AGENTS_KEY, PLATFORM_AGENTS, null);
      seed(MODES_KEY, PLATFORM_MODES, null);
      assertThat(read(AGENTS_KEY, DEFAULT_TENANT_UUID)).isNull();

      // Act
      runMigration();

      // Assert
      assertThat(read(AGENTS_KEY, DEFAULT_TENANT_UUID)).isEqualTo(PLATFORM_AGENTS);
      assertThat(read(AGENTS_KEY, other.getId())).isEqualTo(PLATFORM_AGENTS);
      assertThat(read(MODES_KEY, DEFAULT_TENANT_UUID)).isEqualTo(PLATFORM_MODES);
      assertThat(read(MODES_KEY, other.getId())).isEqualTo(PLATFORM_MODES);
    }

    @Test
    @DisplayName("The platform rows are gone once copied")
    void given_platformRowOnly_should_deleteThePlatformRows() throws Exception {
      // Arrange
      seed(AGENTS_KEY, PLATFORM_AGENTS, null);
      seed(MODES_KEY, PLATFORM_MODES, null);

      // Act
      runMigration();

      // Assert
      assertThat(platformRowCount()).isZero();
    }

    @Test
    @DisplayName("A tenant that already chose its own value keeps it")
    void given_tenantAlreadySet_should_keepItsOwnValue() throws Exception {
      // Arrange
      Tenant other = tenantHelper.createTenantWithCurrentUser("autonomous-defaults-own");
      seed(AGENTS_KEY, PLATFORM_AGENTS, null);
      seed(AGENTS_KEY, TENANT_OWN_AGENTS, other.getId());

      // Act
      runMigration();

      // Assert
      assertThat(read(AGENTS_KEY, other.getId())).isEqualTo(TENANT_OWN_AGENTS);
      assertThat(read(AGENTS_KEY, DEFAULT_TENANT_UUID)).isEqualTo(PLATFORM_AGENTS);
      assertThat(platformRowCount()).isZero();
    }

    @Test
    @DisplayName("A deployment with no platform row is left untouched")
    void given_noPlatformRow_should_createNothing() throws Exception {
      // Arrange
      Tenant other = tenantHelper.createTenantWithCurrentUser("autonomous-defaults-empty");

      // Act
      runMigration();

      // Assert
      assertThat(read(AGENTS_KEY, other.getId())).isNull();
      assertThat(read(MODES_KEY, DEFAULT_TENANT_UUID)).isNull();
    }
  }

  @Nested
  @DisplayName("Idempotency")
  class Idempotency {

    @Test
    @DisplayName("Re-running the migration changes nothing")
    void given_alreadyAppliedMigration_should_rerunWithoutChangingTheRows() throws Exception {
      // Arrange
      Tenant other = tenantHelper.createTenantWithCurrentUser("autonomous-defaults-rerun");
      seed(AGENTS_KEY, PLATFORM_AGENTS, null);
      runMigration();
      seed(AGENTS_KEY, "[\"edited-after-upgrade\"]", other.getId(), true);

      // Act
      runMigration();

      // Assert
      assertThat(read(AGENTS_KEY, other.getId())).isEqualTo("[\"edited-after-upgrade\"]");
      assertThat(read(AGENTS_KEY, DEFAULT_TENANT_UUID)).isEqualTo(PLATFORM_AGENTS);
      assertThat(rowCount(AGENTS_KEY)).isEqualTo(tenantCount());
    }
  }

  // -- helpers --

  private void seed(String key, String value, String tenantId) {
    seed(key, value, tenantId, false);
  }

  /**
   * Seeding and reading go through the ORM, so with {@code parameters} active they need every
   * tenant in scope and the platform-write marker for the platform rows the migration moves.
   */
  private void scopeEveryTenantAndPlatformWrite() {
    @SuppressWarnings("unchecked")
    List<String> tenantIds =
        entityManager.createNativeQuery("SELECT tenant_id FROM tenants").getResultList();
    tenantScopedTransaction.setScopeOnCurrentTransaction(TxCtx.forTenants(tenantIds));
    tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
  }

  private void seed(String key, String value, String tenantId, boolean replace) {
    scopeEveryTenantAndPlatformWrite();
    if (replace) {
      entityManager
          .createNativeQuery(
              "UPDATE parameters SET parameter_value = :value"
                  + " WHERE parameter_key = :key AND tenant_id = :tenantId")
          .setParameter("value", value)
          .setParameter("key", key)
          .setParameter("tenantId", tenantId)
          .executeUpdate();
      return;
    }
    entityManager
        .createNativeQuery(
            "INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)"
                + " VALUES (gen_random_uuid()::text, :key, :value, :tenantId)")
        .setParameter("key", key)
        .setParameter("value", value)
        .setParameter("tenantId", tenantId)
        .executeUpdate();
  }

  private String read(String key, String tenantId) {
    scopeEveryTenantAndPlatformWrite();
    entityManager.clear();
    return settingRepository
        .findByKeyAndTenantId(key, tenantId)
        .map(Setting::getValue)
        .orElse(null);
  }

  private long platformRowCount() {
    scopeEveryTenantAndPlatformWrite();
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM parameters WHERE tenant_id IS NULL"
                        + " AND parameter_key IN (:agents, :modes)")
                .setParameter("agents", AGENTS_KEY)
                .setParameter("modes", MODES_KEY)
                .getSingleResult())
        .longValue();
  }

  private long rowCount(String key) {
    scopeEveryTenantAndPlatformWrite();
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM parameters WHERE parameter_key = :key")
                .setParameter("key", key)
                .getSingleResult())
        .longValue();
  }

  private long tenantCount() {
    return ((Number)
            entityManager.createNativeQuery("SELECT count(*) FROM tenants").getSingleResult())
        .longValue();
  }

  private void runMigration() {
    entityManager.flush();
    entityManager.unwrap(Session.class).doWork(connection -> migrate(connection));
  }

  private void migrate(Connection connection) {
    try {
      migration.migrate(contextOn(connection));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static Context contextOn(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }
}
