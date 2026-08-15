package io.openaev.runner;

import io.openaev.annotation.AllowRawJdbc;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.stereotype.Component;

/**
 * Realigns, at EVERY startup, the tenant attribution of autonomous child rows ({@code
 * autonomous_events} and {@code autonomous_directives}) with their parent {@code autonomous_runs}
 * row, so multi-tenancy v2 stays sound on these tables across upgrades and rolling deployments
 * (#7396).
 *
 * <p>Why rows can be misattributed at all: until the tables became tenant-active, both child tables
 * stamped {@code tenant_id} through {@code TenantBaseListener}'s thread-local default, which held
 * the DEFAULT tenant on the orchestrator's non-prefixed callback route - so children could land in
 * a different tenant than their parent run. Once the tables are tenant-active such rows are
 * invisible to run-scoped reads, and worse: {@code AutonomousEventService} assigns {@code
 * max(sequence) + 1} through the tenant-filtered {@code findMaxSequence}, so a hidden row's
 * sequence is recomputed and rejected by the GLOBAL unique {@code (autonomous_event_run_id,
 * autonomous_event_sequence)} index, wedging every subsequent append to that run - and the restart
 * purge is itself tenant-filtered, so not even a run restart can delete the hidden row. The parent
 * run's tenant is authoritative: runs are created on authenticated, tenant-scoped operator routes,
 * while children inherited whatever thread-local happened to be current at insert time.
 *
 * <p>Why EVERY boot and not a one-shot migration: during a rolling deployment a not-yet-upgraded
 * node keeps writing default-stamped children AFTER an upgraded node's one-shot repair already ran,
 * and Flyway never reruns a versioned migration, so such rows would stay misattributed forever.
 * Re-running the repair on each boot makes any residual row self-heal at the latest on the next
 * boot of any node - typically by the remaining boots of the same rollout - without manual SQL. The
 * write window itself is deliberately NOT closed: a database trigger would introduce a second,
 * database-side attribution authority against the v2 principle that INSERT attribution is an
 * application-layer decision (B3, {@code TenantWriteScopeResolver}), and a repair-then-activate
 * release train would keep the cross-tenant read gap of #7396 open one more release for every
 * install, including single-node deployments that have no overlap window at all.
 *
 * <p>Why this shape and not the existing frameworks: a Flyway REPEATABLE migration cannot live in
 * {@code io.openaev.migration} - the Migrations Guard CI check enforces an append-only, versioned
 * file list there (an {@code R__} file sorts before the released {@code V*} block and can never
 * satisfy it). A {@code RuntimeMigration} / {@code DataPack} is tracked once-per-tenant (the same
 * never-rerun defect), runs through the ORM whose statement inspector would hide exactly the
 * cross-tenant rows this repair exists to fix, and is profile-disabled in tests.
 *
 * <p>Raw JDBC is required, not convenience: at startup there is no request scope, and the repair
 * must see rows OUTSIDE any tenant scope to realign them; a plain {@link DataSource} connection
 * bypasses the Hibernate-level tenant statement inspector by construction. The write only copies
 * the parent run's {@code tenant_id} onto its own children - the same invariant application code
 * enforces on every insert.
 *
 * <p>Ordering and failure: the constructor takes {@link FlywayMigrationInitializer} purely as an
 * ordering edge, so the repair runs after schema migrations (fresh installs create the tables in
 * that step) and inside singleton initialization - strictly before the web connectors accept
 * traffic. A failure aborts startup exactly like a failing migration would. Idempotent and
 * lock-light: plain row UPDATEs touching only rows whose tenant differs from their run's (a no-op
 * on fresh, healthy and already-repaired databases). Orphaned children (parent run already deleted)
 * are skipped - there is no parent to align with and no live run to break. Sequences are never
 * touched - they are already unique per run globally, so realignment cannot introduce a duplicate.
 */
@Component
@Slf4j
@AllowRawJdbc(
    reason =
        "startup repair of cross-tenant misattributed rows: there is no tenant scope at boot and"
            + " the inspector-scoped ORM would hide exactly the rows this repair must realign;"
            + " the write only copies the parent run's tenant_id onto its own children")
public class AutonomousChildTenantRealignRepair {

  private final DataSource dataSource;

  public AutonomousChildTenantRealignRepair(
      DataSource dataSource, FlywayMigrationInitializer flywayMigrationInitializer) {
    // flywayMigrationInitializer is an ordering edge only: initializing this bean after it
    // guarantees the schema (and any pending versioned migration) is in place before the repair.
    this.dataSource = dataSource;
  }

  @PostConstruct
  void repairAtStartup() {
    try (Connection connection = dataSource.getConnection()) {
      int repaired = realign(connection);
      if (repaired > 0) {
        log.info(
            "[Autonomous] Realigned {} child row(s) onto their parent run's tenant at startup",
            repaired);
      } else {
        log.debug("[Autonomous] Child tenant attribution already aligned at startup");
      }
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Autonomous child tenant realignment failed at startup - refusing to serve traffic"
              + " with potentially misattributed rows",
          e);
    }
  }

  /**
   * Runs the two idempotent realignment UPDATEs on the given connection and returns how many rows
   * were repaired. Exposed on a caller-supplied connection so tests can drive the repair inside
   * their own (rolled-back) transaction; {@link #repairAtStartup()} runs it on a fresh autocommit
   * connection where each UPDATE commits independently - a crash between the two simply leaves the
   * second table for the next boot.
   */
  public int realign(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      int events =
          statement.executeUpdate(
              """
              UPDATE autonomous_events e
                 SET tenant_id = r.tenant_id
                FROM autonomous_runs r
               WHERE e.autonomous_event_run_id = r.autonomous_run_id
                 AND e.tenant_id <> r.tenant_id
              """);
      int directives =
          statement.executeUpdate(
              """
              UPDATE autonomous_directives d
                 SET tenant_id = r.tenant_id
                FROM autonomous_runs r
               WHERE d.autonomous_directive_run_id = r.autonomous_run_id
                 AND d.tenant_id <> r.tenant_id
              """);
      return events + directives;
    }
  }
}
