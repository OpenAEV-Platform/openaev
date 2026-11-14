package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the statements the inspector rewrites for a dual-scope table on the real database, against a
 * scratch table that has the column types the guarded {@code parameters}, {@code groups} and {@code
 * roles} tables mix: a nullable boolean, a nullable timestamp, a text column and the nullable
 * {@code tenant_id}. A string assertion on the rewritten SQL cannot show that PostgreSQL accepts
 * it, which is what these tests are for: a {@code NULL} literal or a bind parameter going into a
 * non-text column, a platform row, a tenant outside the scope, and an {@code UPDATE} or {@code ON
 * CONFLICT DO UPDATE} that re-assigns {@code tenant_id}.
 *
 * <p>The table is armed in a {@link TenantStatementInspector} of its own and the rewritten SQL is
 * run on the transaction's connection, so the application's active-tables list is not touched.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Dual-scope written tenant, executed on typed columns")
class DualScopeWrittenValueExecutionTest extends IntegrationTest {

  private static final String OTHER_TENANT = "00000000-0000-4000-8000-0000000000b2";
  private static final String COLUMNS =
      "(scratch_id, scratch_flag, scratch_at, scratch_note, tenant_id)";
  private static final String REFUSED = "42501";

  private final TenantStatementInspector inspector =
      new TenantStatementInspector(new TenantTables(Set.of(), Set.of("scratch_dual")));

  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @FunctionalInterface
  private interface Binder {
    void bind(PreparedStatement statement) throws SQLException;
  }

  @BeforeEach
  void armScratchTable() {
    run(
        "CREATE TEMP TABLE IF NOT EXISTS scratch_dual (scratch_id varchar PRIMARY KEY,"
            + " scratch_flag boolean, scratch_at timestamptz, scratch_note text,"
            + " tenant_id varchar) ON COMMIT DROP",
        false,
        statement -> {});
    tenantScopedTransaction.setScopeOnCurrentTransaction(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
  }

  /** Runs {@code sql} on the transaction's connection, through the inspector when asked. */
  private int run(String sql, boolean inspected, Binder binder) {
    int[] affected = new int[1];
    entityManager
        .unwrap(Session.class)
        .doWork(
            (Connection connection) -> {
              // A refused statement aborts the transaction: undo only it, so the test can still
              // read the table afterwards.
              Savepoint savepoint = connection.setSavepoint();
              try (PreparedStatement statement =
                  connection.prepareStatement(inspected ? inspector.inspect(sql) : sql)) {
                binder.bind(statement);
                affected[0] = statement.executeUpdate();
              } catch (SQLException e) {
                connection.rollback(savepoint);
                throw e;
              }
            });
    return affected[0];
  }

  private static String refusal(RuntimeException e) {
    Throwable cause = e;
    while (cause != null && !(cause instanceof SQLException)) {
      cause = cause.getCause();
    }
    return cause == null ? "" : ((SQLException) cause).getSQLState();
  }

  private int count(String where) {
    int[] rows = new int[1];
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (var statement = connection.createStatement();
                  var result =
                      statement.executeQuery("SELECT count(*) FROM scratch_dual WHERE " + where)) {
                result.next();
                rows[0] = result.getInt(1);
              }
            });
    return rows[0];
  }

  private int insertRow(String id, String tenantId) {
    return run(
        "INSERT INTO scratch_dual " + COLUMNS + " VALUES (?, ?, ?, NULL, ?)",
        true,
        statement -> {
          statement.setString(1, id);
          statement.setBoolean(2, true);
          statement.setTimestamp(3, Timestamp.from(Instant.now()));
          if (tenantId == null) {
            statement.setNull(4, Types.VARCHAR);
          } else {
            statement.setString(4, tenantId);
          }
        });
  }

  @Nested
  @DisplayName("INSERT ... VALUES")
  class InsertValues {

    @Test
    @DisplayName("given_typedBindsAndANullLiteral_should_insertTheTenantRow")
    void given_typedBindsAndANullLiteral_should_insertTheTenantRow() {
      // Act
      int inserted = insertRow("a1", Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertEquals(1, inserted);
      assertEquals(
          1, count("scratch_id = 'a1' AND tenant_id = '" + Tenant.DEFAULT_TENANT_UUID + "'"));
    }

    @Test
    @DisplayName("given_nullLiteralsIntoBooleanAndTimestampColumns_should_insertTheTenantRow")
    void given_nullLiteralsIntoBooleanAndTimestampColumns_should_insertTheTenantRow() {
      // Act
      int inserted =
          run(
              "INSERT INTO scratch_dual " + COLUMNS + " VALUES (?, NULL, NULL, NULL, ?)",
              true,
              statement -> {
                statement.setString(1, "a2");
                statement.setString(2, Tenant.DEFAULT_TENANT_UUID);
              });

      // Assert
      assertEquals(1, inserted);
      assertEquals(1, count("scratch_id = 'a2' AND scratch_flag IS NULL AND scratch_at IS NULL"));
    }

    @Test
    @DisplayName("given_nullBindsIntoBooleanAndTimestampColumns_should_insertTheTenantRow")
    void given_nullBindsIntoBooleanAndTimestampColumns_should_insertTheTenantRow() {
      // Act
      int inserted =
          run(
              "INSERT INTO scratch_dual " + COLUMNS + " VALUES (?, ?, ?, ?, ?)",
              true,
              statement -> {
                statement.setString(1, "a3");
                statement.setNull(2, Types.BOOLEAN);
                statement.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
                statement.setNull(4, Types.VARCHAR);
                statement.setString(5, Tenant.DEFAULT_TENANT_UUID);
              });

      // Assert
      assertEquals(1, inserted);
    }

    @Test
    @DisplayName("given_severalRows_should_insertEveryRowAndCheckEachTenant")
    void given_severalRows_should_insertEveryRowAndCheckEachTenant() {
      // Arrange
      String sql =
          "INSERT INTO scratch_dual "
              + COLUMNS
              + " VALUES (?, ?, NULL, NULL, ?), (?, ?, NULL, NULL, ?)";

      // Act
      int inserted =
          run(
              sql,
              true,
              statement -> {
                statement.setString(1, "m1");
                statement.setBoolean(2, true);
                statement.setString(3, Tenant.DEFAULT_TENANT_UUID);
                statement.setString(4, "m2");
                statement.setBoolean(5, false);
                statement.setString(6, Tenant.DEFAULT_TENANT_UUID);
              });
      RuntimeException refused =
          assertThrows(
              RuntimeException.class,
              () ->
                  run(
                      sql,
                      true,
                      statement -> {
                        statement.setString(1, "m3");
                        statement.setBoolean(2, true);
                        statement.setString(3, Tenant.DEFAULT_TENANT_UUID);
                        statement.setString(4, "m4");
                        statement.setBoolean(5, true);
                        statement.setString(6, OTHER_TENANT);
                      }));

      // Assert
      assertEquals(2, inserted);
      assertEquals(REFUSED, refusal(refused));
    }

    @Test
    @DisplayName("given_aPlatformWriteTransaction_should_insertAPlatformRow")
    void given_aPlatformWriteTransaction_should_insertAPlatformRow() {
      // Arrange
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();

      // Act
      int inserted = insertRow("p1", null);

      // Assert
      assertEquals(1, inserted);
      assertEquals(1, count("scratch_id = 'p1' AND tenant_id IS NULL"));
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseAPlatformRow")
    void given_noPlatformWriteMarker_should_refuseAPlatformRow() {
      // Act
      RuntimeException refused = assertThrows(RuntimeException.class, () -> insertRow("p2", null));

      // Assert
      assertEquals(REFUSED, refusal(refused));
    }

    @Test
    @DisplayName("given_aTenantOutsideTheScope_should_refuseTheRow")
    void given_aTenantOutsideTheScope_should_refuseTheRow() {
      // Act
      RuntimeException refused =
          assertThrows(RuntimeException.class, () -> insertRow("o1", OTHER_TENANT));

      // Assert
      assertEquals(REFUSED, refusal(refused));
    }
  }

  @Nested
  @DisplayName("UPDATE and ON CONFLICT DO UPDATE re-assigning tenant_id")
  class ReassignTenant {

    private int updateTenant(String id, String tenantId) {
      return run(
          "UPDATE scratch_dual SET tenant_id = ? WHERE scratch_id = ?",
          true,
          statement -> {
            if (tenantId == null) {
              statement.setNull(1, Types.VARCHAR);
            } else {
              statement.setString(1, tenantId);
            }
            statement.setString(2, id);
          });
    }

    @Test
    @DisplayName("given_noPlatformWriteMarker_should_refuseTurningATenantRowIntoAPlatformRow")
    void given_noPlatformWriteMarker_should_refuseTurningATenantRowIntoAPlatformRow() {
      // Arrange
      insertRow("u1", Tenant.DEFAULT_TENANT_UUID);

      // Act
      RuntimeException refused =
          assertThrows(RuntimeException.class, () -> updateTenant("u1", null));

      // Assert
      assertEquals(REFUSED, refusal(refused));
      assertEquals(
          1, count("scratch_id = 'u1' AND tenant_id = '" + Tenant.DEFAULT_TENANT_UUID + "'"));
    }

    @Test
    @DisplayName("given_aTenantOutsideTheScope_should_refuseMovingTheRowThere")
    void given_aTenantOutsideTheScope_should_refuseMovingTheRowThere() {
      // Arrange
      insertRow("u2", Tenant.DEFAULT_TENANT_UUID);

      // Act
      RuntimeException refused =
          assertThrows(RuntimeException.class, () -> updateTenant("u2", OTHER_TENANT));

      // Assert
      assertEquals(REFUSED, refusal(refused));
    }

    @Test
    @DisplayName("given_theSameTenantOrAPlatformWriteMarker_should_acceptTheUpdate")
    void given_theSameTenantOrAPlatformWriteMarker_should_acceptTheUpdate() {
      // Arrange
      insertRow("u3", Tenant.DEFAULT_TENANT_UUID);

      // Act
      int sameTenant = updateTenant("u3", Tenant.DEFAULT_TENANT_UUID);
      tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
      int toPlatform = updateTenant("u3", null);

      // Assert
      assertEquals(1, sameTenant);
      assertEquals(1, toPlatform);
      assertEquals(1, count("scratch_id = 'u3' AND tenant_id IS NULL"));
    }

    @Test
    @DisplayName("given_aDoUpdateThatClearsTenantId_should_refuseItWithoutThePlatformWriteMarker")
    void given_aDoUpdateThatClearsTenantId_should_refuseItWithoutThePlatformWriteMarker() {
      // Arrange
      insertRow("c1", Tenant.DEFAULT_TENANT_UUID);

      // Act
      RuntimeException refused =
          assertThrows(
              RuntimeException.class,
              () ->
                  run(
                      "INSERT INTO scratch_dual "
                          + COLUMNS
                          + " VALUES (?, NULL, NULL, NULL, ?) ON CONFLICT (scratch_id) DO UPDATE"
                          + " SET tenant_id = NULL",
                      true,
                      statement -> {
                        statement.setString(1, "c1");
                        statement.setString(2, Tenant.DEFAULT_TENANT_UUID);
                      }));

      // Assert
      assertEquals(REFUSED, refusal(refused));
      assertEquals(
          1, count("scratch_id = 'c1' AND tenant_id = '" + Tenant.DEFAULT_TENANT_UUID + "'"));
    }
  }

  @Nested
  @DisplayName("INSERT ... SELECT")
  class InsertSelect {

    /** A tenant that depends on how many times the expression is evaluated: odd, then even. */
    private int insertWithAnExpressionThatChangesBetweenEvaluations(String id, long firstValue) {
      run("CREATE TEMP SEQUENCE IF NOT EXISTS scratch_seq", false, statement -> {});
      run(
          "DO $$ BEGIN PERFORM setval('scratch_seq', " + firstValue + ", false); END $$",
          false,
          statement -> {});
      return run(
          "INSERT INTO scratch_dual (scratch_id, tenant_id) SELECT '"
              + id
              + "', CASE WHEN nextval('scratch_seq') % 2 = 1 THEN '"
              + Tenant.DEFAULT_TENANT_UUID
              + "' ELSE NULL END",
          true,
          statement -> {});
    }

    @Test
    @DisplayName("given_aTenantThatChangesBetweenEvaluations_should_writeTheValueItChecked")
    void given_aTenantThatChangesBetweenEvaluations_should_writeTheValueItChecked() {
      // Act
      int inserted = insertWithAnExpressionThatChangesBetweenEvaluations("s1", 1);

      // Assert
      assertEquals(1, inserted);
      assertEquals(0, count("scratch_id = 's1' AND tenant_id IS NULL"));
      assertEquals(
          1, count("scratch_id = 's1' AND tenant_id = '" + Tenant.DEFAULT_TENANT_UUID + "'"));
    }

    @Test
    @DisplayName("given_aPlatformTenantWithoutTheMarker_should_refuseTheRow")
    void given_aPlatformTenantWithoutTheMarker_should_refuseTheRow() {
      // Act
      RuntimeException refused =
          assertThrows(
              RuntimeException.class,
              () -> insertWithAnExpressionThatChangesBetweenEvaluations("s2", 2));

      // Assert
      assertEquals(REFUSED, refusal(refused));
      assertEquals(0, count("scratch_id = 's2'"));
    }
  }
}
