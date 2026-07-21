package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Converges the search engine back to database truth after the accuracy fixes (issue #6753).
 *
 * <ol>
 *   <li>Completes the asset taxonomy remodel: agentless {@code Endpoint} rows categorized {@code
 *       AI_TARGET} or {@code SECURITY_PLATFORM} were missed by the original demotion list and kept
 *       inflating the endpoint index versus the Host inventory.
 *   <li>Deletes EVERY {@code indexing_status} row: at the next startup the engine drivers drop and
 *       recreate all indexes and fully re-feed them from PostgreSQL. All historical garbage
 *       accumulated by the delete-propagation and cursor bugs (stale simulations, injects,
 *       expectations, findings, per-player expectation docs, wrong platform attributions) is
 *       eliminated by construction, with no manual action: statistics are exact as soon as the
 *       rebuild completes.
 * </ol>
 *
 * <p>Both steps are idempotent and lock-light (targeted UPDATE on a small table + full DELETE on a
 * tiny bookkeeping table).
 */
@Component
public class V6_20260717220000000__Engine_accuracy_full_reindex extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 1. Demote the taxonomy remodel leftovers (agent-bearing rows are never demoted:
      // agents only exist on Endpoint, demoting would orphan them) --
      statement.execute(
          "UPDATE assets SET asset_type = 'Asset' "
              + "WHERE asset_type = 'Endpoint' "
              + "AND asset_category IN ('AI_TARGET', 'SECURITY_PLATFORM') "
              + "AND NOT EXISTS (SELECT 1 FROM agents ag WHERE ag.agent_asset = assets.asset_id);");

      // -- 2. Force a full drop-and-rebuild of every search index at startup --
      statement.execute("DELETE FROM indexing_status;");
    }
  }
}
