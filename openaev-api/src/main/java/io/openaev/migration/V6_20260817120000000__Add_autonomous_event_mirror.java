package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code autonomous_run_event_mirror} JSONB column to {@code autonomous_runs}.
 *
 * <p>An autonomous run authors its finding EVENTS on the SIMULATION workflow, and the same steps
 * are mirrored onto the run's SCENARIO workflow so the scenario carries an exportable attack path.
 * When the orchestrator REUSES an existing event (attaching several actions to one event instead of
 * duplicating it), the mirror must reattach the scenario twin to the SAME scenario event. This
 * column stores the mapping from each simulation event root id to its scenario twin ({@code {"<sim
 * event id>": "<scenario event id>"}}) so the exported scenario shares events exactly like the
 * executing simulation side. It is the event-level counterpart of {@code
 * autonomous_run_step_mirror}. Internal bookkeeping only; never exposed on the API.
 *
 * <p>Additive, idempotent, and lock-light: a nullable {@code ADD COLUMN} is metadata-only on
 * PostgreSQL 11+ (no table rewrite), and {@code IF NOT EXISTS} makes re-running a no-op.
 */
@Component
public class V6_20260817120000000__Add_autonomous_event_mirror extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE autonomous_runs ADD COLUMN IF NOT EXISTS autonomous_run_event_mirror jsonb;");
    }
  }
}
