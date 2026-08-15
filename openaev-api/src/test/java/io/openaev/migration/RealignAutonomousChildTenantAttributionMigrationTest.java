package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the pre-activation tenant repair for autonomous child rows: an event or directive
 * stamped with a different tenant than its parent run (the {@code TenantBaseListener} thread-local
 * default on the orchestrator's callback route did exactly that) is realigned to the run's tenant,
 * already-aligned rows and their sequences are left untouched, orphaned children without a parent
 * run are skipped, and re-running the migration is a no-op.
 *
 * <p>All seeding and ground-truth reads go over native SQL / raw JDBC on the test's own connection,
 * mirroring {@code AutonomousRunHttpIsolationTest}: the migration is plain SQL, so the test must
 * observe raw columns rather than inspector-scoped JPA reads.
 *
 * <p>{@code @Transactional} so the seeded rows and migration side effects roll back with the test
 * transaction.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Realign autonomous child tenant attribution migration")
class RealignAutonomousChildTenantAttributionMigrationTest extends IntegrationTest {

  @Autowired private V6_20260815100000000__Realign_autonomous_child_tenant_attribution migration;

  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String runTenant;
  private String strayTenant;
  private String runId;

  @BeforeEach
  void seedRun() throws Exception {
    // The stray tenant stands in for the legacy thread-local default: any existing tenant row
    // different from the run's works, the repair keys on the mismatch, not on a specific id.
    runTenant = tenantHelper.createTenantWithCurrentUser("realign-run").getId();
    strayTenant = tenantHelper.createTenantWithCurrentUser("realign-stray").getId();
    runId = seedRun(runTenant);
  }

  @Test
  @DisplayName("Realigns misaligned children to the parent run's tenant, keeping aligned rows")
  void misaligned_children_are_realigned() {
    String strayEventId = seedEvent(strayTenant, runId, 7);
    String alignedEventId = seedEvent(runTenant, runId, 8);
    String strayDirectiveId = seedDirective(strayTenant, runId);

    runMigration();

    assertThat(rawColumn("autonomous_events", "tenant_id", "autonomous_event_id", strayEventId))
        .isEqualTo(runTenant);
    assertThat(rawColumn("autonomous_events", "tenant_id", "autonomous_event_id", alignedEventId))
        .isEqualTo(runTenant);
    assertThat(
            rawColumn(
                "autonomous_directives", "tenant_id", "autonomous_directive_id", strayDirectiveId))
        .isEqualTo(runTenant);
    // Realignment repairs attribution only - the timeline's sequence numbers must survive as-is.
    assertThat(
            rawColumn(
                "autonomous_events",
                "autonomous_event_sequence",
                "autonomous_event_id",
                strayEventId))
        .isEqualTo("7");
  }

  @Test
  @DisplayName("Leaves orphaned children (no parent run) untouched")
  void orphaned_children_are_left_untouched() {
    String orphanEventId = seedEvent(strayTenant, UUID.randomUUID().toString(), 1);

    runMigration();

    assertThat(rawColumn("autonomous_events", "tenant_id", "autonomous_event_id", orphanEventId))
        .isEqualTo(strayTenant);
  }

  @Test
  @DisplayName("Re-running the migration is a no-op (idempotent)")
  void migration_is_idempotent() {
    String strayEventId = seedEvent(strayTenant, runId, 1);

    runMigration();
    assertThatCode(this::runMigration).doesNotThrowAnyException();

    assertThat(rawColumn("autonomous_events", "tenant_id", "autonomous_event_id", strayEventId))
        .isEqualTo(runTenant);
  }

  private void runMigration() {
    entityManager.flush();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(
                    new Context() {
                      @Override
                      public Configuration getConfiguration() {
                        return null;
                      }

                      @Override
                      public java.sql.Connection getConnection() {
                        return connection;
                      }
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private String seedRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'COMPLETED')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedEvent(String tenantId, String runId, long sequence) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_events (autonomous_event_id, tenant_id,"
                + " autonomous_event_run_id, autonomous_event_sequence, autonomous_event_type,"
                + " autonomous_event_title)"
                + " VALUES (:id, :tenant, :run, :sequence, 'STATUS', 'Legacy row')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .setParameter("sequence", sequence)
        .executeUpdate();
    return id;
  }

  private String seedDirective(String tenantId, String runId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_directives (autonomous_directive_id, tenant_id,"
                + " autonomous_directive_run_id, autonomous_directive_content,"
                + " autonomous_directive_status)"
                + " VALUES (:id, :tenant, :run, 'Focus on the domain controller', 'PENDING')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
    return id;
  }

  // Ground truth on the raw column over plain JDBC (no inspector rewrite), on the test's own
  // connection so it sees the uncommitted seed. Null when the row does not exist, so a missing
  // row fails the equality loudly.
  private String rawColumn(String table, String column, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT " + column + " FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }
}
