package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.V6_20261112060000000__Add_tenant_id_to_execution_traces.TABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectTestStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectTestStatusRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies, on the live schema, that {@code execution_traces} carries its own {@code tenant_id},
 * and that the backfill attributes each row to the tenant of the inject its status belongs to.
 *
 * <p>{@code @Transactional} so the DDL the backfill tests apply (dropping {@code NOT NULL} to seed
 * unattributed rows) rolls back with the test transaction.
 *
 * <p>The test's own SQL runs on the transaction's raw JDBC connection, as Flyway runs the migration
 * itself: schema-level work belongs to no tenant scope, and the tenant statement inspector refuses
 * DDL.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261112060000000: tenant_id on execution_traces")
class AddTenantIdToExecutionTracesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261112060000000__Add_tenant_id_to_execution_traces migration;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectTestStatusRepository injectTestStatusRepository;

  @BeforeEach
  void resetComposers() {
    injectComposer.reset();
    injectStatusComposer.reset();
  }

  @AfterEach
  void clearThreadLocal() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Schema")
  class Schema {

    @Test
    @DisplayName("The table has a NOT NULL tenant_id with no default")
    void given_migratedSchema_should_haveNotNullTenantIdWithoutDefault() {
      // Act
      List<Object[]> columns =
          nativeRows(
              "SELECT is_nullable, column_default FROM information_schema.columns"
                  + " WHERE table_schema = current_schema() AND table_name = '"
                  + TABLE
                  + "' AND column_name = 'tenant_id'");

      // Assert
      assertThat(columns).hasSize(1);
      assertThat(columns.getFirst()[0]).isEqualTo("NO");
      assertThat(columns.getFirst()[1]).isNull();
    }

    @Test
    @DisplayName("The table references tenants with ON DELETE CASCADE")
    void given_migratedSchema_should_referenceTenantsWithCascade() {
      // Act
      long foreignKeys =
          nativeCount(
              "SELECT count(*) FROM pg_constraint c"
                  + " JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]"
                  + " WHERE c.contype = 'f' AND c.conrelid = '"
                  + TABLE
                  + "'::regclass AND c.confrelid = 'tenants'::regclass"
                  + " AND array_length(c.conkey, 1) = 1 AND a.attname = 'tenant_id'"
                  + " AND c.confdeltype = 'c'");

      // Assert
      assertThat(foreignKeys).isEqualTo(1);
    }

    @Test
    @DisplayName("The table has an index led by tenant_id")
    void given_migratedSchema_should_indexTenantId() {
      // Act
      long indexes =
          nativeCount(
              "SELECT count(*) FROM pg_index i"
                  + " JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]"
                  + " WHERE i.indrelid = '"
                  + TABLE
                  + "'::regclass AND a.attname = 'tenant_id'");

      // Assert
      assertThat(indexes).isPositive();
    }
  }

  @Nested
  @DisplayName("Backfill")
  class Backfill {

    private Tenant tenantB;
    private Tenant tenantC;
    private String traceOfInjectStatus;
    private String traceOfInjectTestStatus;
    private String traceOfStatusWithoutInject;
    private String traceAlreadyAttributed;
    private String traceOfBothStatuses;

    @BeforeEach
    void seedUnattributedRows() {
      tenantB = persistTenant("trace-owner-b");
      tenantC = persistTenant("trace-owner-c");
      Inject injectOfB = persistInject(tenantB, true);
      Inject injectOfC = persistInject(tenantC, false);
      InjectTestStatus testStatus = new InjectTestStatus();
      testStatus.setName(ExecutionStatus.PENDING);
      testStatus.setInject(injectOfC);
      testStatus = injectTestStatusRepository.save(testStatus);
      entityManager.flush();

      execute("ALTER TABLE " + TABLE + " ALTER COLUMN tenant_id DROP NOT NULL");
      String statusWithoutInject = id();
      execute(
          "INSERT INTO injects_statuses (status_id, status_name) VALUES ('"
              + statusWithoutInject
              + "', 'PENDING')");
      traceOfInjectStatus =
          seedTrace("execution_inject_status_id", injectOfB.getStatus().orElseThrow().getId());
      traceOfInjectTestStatus = seedTrace("execution_inject_test_status_id", testStatus.getId());
      traceOfStatusWithoutInject = seedTrace("execution_inject_status_id", statusWithoutInject);
      String statusOfB = injectOfB.getStatus().orElseThrow().getId();
      traceAlreadyAttributed = seedTrace(statusOfB, null, tenantC.getId());
      traceOfBothStatuses = seedTrace(statusOfB, testStatus.getId(), null);
    }

    @Test
    @DisplayName("A trace of an inject status gets the tenant of that status's inject")
    void given_traceOfInjectStatus_should_takeTheInjectTenant() {
      // Act
      migrateAcrossTenants();

      // Assert
      assertThat(tenantOf(traceOfInjectStatus)).isEqualTo(tenantB.getId());
    }

    @Test
    @DisplayName("A trace of an inject test status gets the tenant of that status's inject")
    void given_traceOfInjectTestStatus_should_takeTheInjectTenant() {
      // Act
      migrateAcrossTenants();

      // Assert
      assertThat(tenantOf(traceOfInjectTestStatus)).isEqualTo(tenantC.getId());
    }

    @Test
    @DisplayName("A trace whose inject status has no inject falls back to the default tenant")
    void given_traceOfStatusWithoutInject_should_fallBackToTheDefaultTenant() {
      // Act
      migrateAcrossTenants();

      // Assert
      assertThat(tenantOf(traceOfStatusWithoutInject)).isEqualTo(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("A trace that already has a tenant is left as it is, whatever its status's inject")
    void given_traceAlreadyAttributed_should_leaveItsTenantUnchanged() {
      // Act
      migrateAcrossTenants();
      migrateAcrossTenants();

      // Assert
      assertThat(tenantOf(traceAlreadyAttributed)).isEqualTo(tenantC.getId());
    }

    @Test
    @DisplayName("A trace of both an inject status of B and an inject test status of C stays in B")
    void given_traceOfBothStatuses_should_takeTheInjectStatusTenant() {
      // Act
      migrateAcrossTenants();
      migrateAcrossTenants();

      // Assert
      assertThat(tenantOf(traceOfBothStatuses)).isEqualTo(tenantB.getId());
    }

    @Test
    @DisplayName("The column is NOT NULL again once the backfill is done")
    void given_backfilledRows_should_restoreNotNull() {
      // Act
      migrateAcrossTenants();

      // Assert
      assertThat(
              nativeRows(
                      "SELECT is_nullable FROM information_schema.columns"
                          + " WHERE table_schema = current_schema() AND table_name = '"
                          + TABLE
                          + "' AND column_name = 'tenant_id'")
                  .getFirst()[0])
          .isEqualTo("NO");
    }

    /**
     * The backfill writes the tenant of every inject it reads, so its scope names all of them: the
     * two owners and the default tenant the orphan falls back to.
     */
    private void migrateAcrossTenants() {
      DefaultTenantScopeTestListener.setAmbientScope(
          entityManager, String.join(",", tenantB.getId(), tenantC.getId(), DEFAULT_TENANT_UUID));
      runMigration();
    }

    private Inject persistInject(Tenant owner, boolean withStatus) {
      DefaultTenantScopeTestListener.setAmbientScope(entityManager, owner.getId());
      TenantContext.setCurrentTenant(owner.getId());
      InjectComposer.Composer composer = injectComposer.forInject(InjectFixture.getDefaultInject());
      if (withStatus) {
        InjectStatus status = InjectStatusFixture.createPendingInjectStatus();
        composer.withInjectStatus(injectStatusComposer.forInjectStatus(status));
      }
      return composer.persist().get();
    }

    private String seedTrace(String statusColumn, String statusId) {
      String trace = id();
      execute(
          "INSERT INTO "
              + TABLE
              + " (execution_trace_id, "
              + statusColumn
              + ", execution_message, execution_status) VALUES ('"
              + trace
              + "', '"
              + statusId
              + "', 'seeded', 'INFO')");
      return trace;
    }

    /**
     * A trace seeded on an inject status and optionally an inject test status, with a tenant
     * already set when {@code presetTenant} is not null.
     */
    private String seedTrace(
        String injectStatusId, String injectTestStatusId, String presetTenant) {
      String trace = id();
      execute(
          "INSERT INTO "
              + TABLE
              + " (execution_trace_id, execution_inject_status_id, execution_inject_test_status_id,"
              + " tenant_id, execution_message, execution_status) VALUES ('"
              + trace
              + "', '"
              + injectStatusId
              + "', "
              + quoted(injectTestStatusId)
              + ", "
              + quoted(presetTenant)
              + ", 'seeded', 'INFO')");
      return trace;
    }

    private String quoted(String value) {
      return value == null ? "NULL" : "'" + value + "'";
    }
  }

  @Nested
  @DisplayName("Idempotency")
  class Idempotency {

    @Test
    @DisplayName("Re-running the migration is a no-op")
    void given_alreadyAppliedMigration_should_rerunWithoutError() {
      // Act + Assert
      assertThatCode(AddTenantIdToExecutionTracesMigrationTest.this::runMigration)
          .doesNotThrowAnyException();
    }
  }

  // -- helpers --

  private Tenant persistTenant(String name) {
    return tenantRepository.save(
        TenantFixture.getTenant(name + "-" + UUID.randomUUID().toString().substring(0, 8)));
  }

  private static String id() {
    return UUID.randomUUID().toString();
  }

  private void runMigration() {
    // The seeded rows queue their foreign key checks: PostgreSQL refuses an ALTER while any is
    // pending
    execute("SET CONSTRAINTS ALL IMMEDIATE");
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(contextOn(connection));
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private void execute(String sql) {
    onRawConnection(
        connection -> {
          try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
          }
        });
  }

  private String tenantOf(String traceId) {
    List<Object[]> rows =
        nativeRows(
            "SELECT tenant_id FROM " + TABLE + " WHERE execution_trace_id = '" + traceId + "'");
    assertThat(rows).as("trace %s", traceId).hasSize(1);
    return (String) rows.getFirst()[0];
  }

  private long nativeCount(String sql) {
    return ((Number) nativeRows(sql).getFirst()[0]).longValue();
  }

  private List<Object[]> nativeRows(String sql) {
    return onRawConnection(
        connection -> {
          List<Object[]> rows = new ArrayList<>();
          try (Statement statement = connection.createStatement();
              ResultSet resultSet = statement.executeQuery(sql)) {
            int columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
              Object[] row = new Object[columns];
              for (int column = 0; column < columns; column++) {
                row[column] = resultSet.getObject(column + 1);
              }
              rows.add(row);
            }
          }
          return rows;
        });
  }

  /** Flushes the persistence context, then runs {@code work} on the transaction's connection. */
  private <T> T onRawConnection(ReturningWork<T> work) {
    entityManager.flush();
    return entityManager.unwrap(Session.class).doReturningWork(work);
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
