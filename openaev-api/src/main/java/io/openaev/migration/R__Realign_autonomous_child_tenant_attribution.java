package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Realigns the tenant attribution of autonomous child rows ({@code autonomous_events} and {@code
 * autonomous_directives}) with their parent {@code autonomous_runs} row, so multi-tenancy v2 can
 * activate on these tables without breaking runs that predate the activation (#7396).
 *
 * <p>Until activation, both child tables stamped {@code tenant_id} through {@code
 * TenantBaseListener}'s thread-local default. On the orchestrator's non-prefixed callback route
 * that thread-local held the DEFAULT tenant, so events (and, for a cross-tenant administrator,
 * directives) could land in a different tenant than their parent run. Once the tables are
 * tenant-active such rows become invisible to run-scoped reads, and worse: {@code
 * AutonomousEventService} assigns {@code max(sequence) + 1} through the now tenant-filtered {@code
 * findMaxSequence}, so a hidden row's sequence would be recomputed and rejected by the GLOBAL
 * unique {@code (autonomous_event_run_id, autonomous_event_sequence)} index, wedging every
 * subsequent append to that run. The parent run's {@code tenant_id} is authoritative: runs are
 * created on authenticated, tenant-scoped operator routes, while children inherited whatever
 * thread-local happened to be current at insert time. Sequences are not touched - they are already
 * unique per run globally, so realignment cannot introduce a duplicate. Orphaned children (parent
 * run already deleted) are skipped - there is no parent to align with and no live run to break.
 *
 * <p>REPEATABLE, re-applied on every startup: {@link #getChecksum()} returns a per-boot value, so
 * Flyway re-runs the repair each time a node boots instead of exactly once. A one-shot versioned
 * migration would leave a rolling deployment exposed: a not-yet-upgraded node can still write a
 * default-stamped child AFTER the repair ran on the first upgraded node, and that row would then
 * stay misattributed forever - hidden from run-scoped reads and able to wedge its run's event
 * sequence, with no self-serve recovery (a restart's timeline purge is itself tenant-filtered, so
 * it cannot delete the hidden row either). With the per-boot re-run, whatever an old node wrote
 * during the rolling window is realigned at the latest on the next boot of any node - typically by
 * the remaining boots of the same rolling deployment - so residual misattribution self-heals
 * without manual SQL. The write window itself is deliberately NOT closed: only a database trigger
 * or a repair-then-activate release train could do that, and both are disproportionate here -
 * attribution is an application-layer decision in multi-tenancy v2 (B3), a trigger would introduce
 * a second, database-side attribution authority for a transient window, and delaying activation by
 * a release would keep the cross-tenant read gap of #7396 open everywhere for the sake of runs live
 * across one upgrade of this EE-only, human-review-cadence feature (its preview flags were removed
 * in #7376).
 *
 * <p>Idempotent and lock-light: plain row UPDATEs that only touch rows whose tenant differs from
 * their run's (a no-op on fresh, healthy and already-repaired databases), no DDL. Flyway records
 * one history row per boot; the checksum doubles as the epoch second of that boot's repair.
 */
@Component
public class R__Realign_autonomous_child_tenant_attribution extends BaseJavaMigration {

  /**
   * Snapshot taken once per JVM: stable within a boot (Flyway resolves, compares and records one
   * consistent value), different across boots more than a second apart. A later boot therefore
   * always resolves a checksum different from the recorded one, which is what makes Flyway re-apply
   * this repeatable migration on every startup.
   */
  private static final int BOOT_CHECKSUM = (int) (System.currentTimeMillis() / 1000L);

  @Override
  public Integer getChecksum() {
    return BOOT_CHECKSUM;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          UPDATE autonomous_events e
             SET tenant_id = r.tenant_id
            FROM autonomous_runs r
           WHERE e.autonomous_event_run_id = r.autonomous_run_id
             AND e.tenant_id <> r.tenant_id;
          """);
      statement.execute(
          """
          UPDATE autonomous_directives d
             SET tenant_id = r.tenant_id
            FROM autonomous_runs r
           WHERE d.autonomous_directive_run_id = r.autonomous_run_id
             AND d.tenant_id <> r.tenant_id;
          """);
    }
  }
}
