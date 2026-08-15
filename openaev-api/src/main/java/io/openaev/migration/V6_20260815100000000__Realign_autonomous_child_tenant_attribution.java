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
 * subsequent append to that run. Repairing attribution here keeps pre-activation timelines readable
 * and live runs appendable: Flyway runs before the application serves traffic, hence strictly
 * before the activated filtering evaluates its first query.
 *
 * <p>The parent run's {@code tenant_id} is authoritative: runs are created on authenticated,
 * tenant-scoped operator routes, while children inherited whatever thread-local happened to be
 * current at insert time. Sequences are not touched - they are already unique per run globally, so
 * realignment cannot introduce a duplicate.
 *
 * <p>Idempotent and lock-light: plain row UPDATEs that only touch rows whose tenant differs from
 * their run's (a no-op on fresh and healthy databases), no DDL. Both tables belong to a
 * preview-flag-gated, human-review-cadence feature, so the scan is small and bounded. Orphaned
 * children (parent run already deleted) are left as-is - there is no parent to align with and no
 * live run to break. During a rolling deployment a not-yet-upgraded node can still write a
 * default-stamped row AFTER this repair ran; that residual window only affects non-default-tenant
 * runs live across the upgrade and closes when the old nodes drain.
 */
@Component
public class V6_20260815100000000__Realign_autonomous_child_tenant_attribution
    extends BaseJavaMigration {

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
