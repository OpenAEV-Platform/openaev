package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repairs parent detection/prevention expectations wrongly force-failed by the expectations
 * expiration manager.
 *
 * <p>Bug: the expiration manager force-failed any expectation whose own score column was still null
 * at expiration time, including PARENT expectations (asset level with agent children, asset group
 * level) whose score must be derived from their children. When a security platform (e.g. Microsoft
 * Defender) had already answered the agents green (PREVENTED/DETECTED) but the parent score was
 * still pending, the manager stamped the parent with an "Expired" failed result: the asset then
 * permanently showed "Not prevented"/"Not detected" while its agents showed green, corrupting the
 * verdicts and every statistic built on the parent rows.
 *
 * <p>This migration finds parent rows carrying the expiration-manager result whose children are
 * actually green, restores their success score, and strips the bogus expiration result. Asset
 * parents are repaired first so asset group parents read the repaired asset scores. Idempotent:
 * repaired rows no longer carry the expiration-manager result, so re-runs match nothing.
 */
@Component
public class V6_20260726230000000__Repair_parent_expectations_failed_by_expiration_manager
    extends BaseJavaMigration {

  /** Source id stamped by the expectations expiration manager on the results it writes. */
  private static final String EXPIRATION_MANAGER_ID = "96e476e0-b9c4-4660-869c-98585adf754d";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1) Asset-level parents: repair when their agent children are green
      statement.execute(
          repairSql(
              /* parentScope= */ "parent.asset_id IS NOT NULL AND parent.agent_id IS NULL",
              /* childJoin= */ "child.inject_id = parent.inject_id"
                  + " AND child.inject_expectation_type = parent.inject_expectation_type"
                  + " AND child.asset_id = parent.asset_id"
                  + " AND child.agent_id IS NOT NULL"));
      // 2) Asset-group-level parents: repair when their (possibly just repaired) asset children
      // are green
      statement.execute(
          repairSql(
              /* parentScope= */ "parent.asset_group_id IS NOT NULL AND parent.asset_id IS NULL"
                  + " AND parent.agent_id IS NULL",
              /* childJoin= */ "child.inject_id = parent.inject_id"
                  + " AND child.inject_expectation_type = parent.inject_expectation_type"
                  + " AND child.asset_group_id = parent.asset_group_id"
                  + " AND child.asset_id IS NOT NULL"
                  + " AND child.agent_id IS NULL"));
    }
  }

  private static String repairSql(String parentScope, String childJoin) {
    String childSuccess =
        "child.inject_expectation_score IS NOT NULL"
            + " AND child.inject_expectation_score >= child.inject_expectation_expected_score";
    return "UPDATE injects_expectations parent SET"
        + " inject_expectation_score = parent.inject_expectation_expected_score,"
        + " inject_expectation_results = COALESCE((SELECT jsonb_agg(elem)"
        + "   FROM jsonb_array_elements(parent.inject_expectation_results::jsonb) elem"
        + "   WHERE elem->>'sourceId' <> '"
        + EXPIRATION_MANAGER_ID
        + "'), '[]'::jsonb)::json,"
        + " inject_expectation_updated_at = now()"
        + " WHERE "
        + parentScope
        + " AND parent.inject_expectation_type IN ('DETECTION', 'PREVENTION')"
        + " AND parent.inject_expectation_score IS NOT NULL"
        + " AND parent.inject_expectation_score < parent.inject_expectation_expected_score"
        + " AND parent.inject_expectation_results IS NOT NULL"
        + " AND jsonb_typeof(parent.inject_expectation_results::jsonb) = 'array'"
        + " AND EXISTS (SELECT 1"
        + "   FROM jsonb_array_elements(parent.inject_expectation_results::jsonb) r"
        + "   WHERE r->>'sourceId' = '"
        + EXPIRATION_MANAGER_ID
        + "')"
        // At least one child exists (a parent with no children is a genuine leaf, keep it)
        + " AND EXISTS (SELECT 1 FROM injects_expectations child WHERE "
        + childJoin
        + ")"
        // Children verdict: group expectations succeed when ANY child is green, non-group
        // expectations require ALL children green
        + " AND ((parent.inject_expectation_group = true"
        + "   AND EXISTS (SELECT 1 FROM injects_expectations child WHERE "
        + childJoin
        + "     AND "
        + childSuccess
        + "))"
        + "  OR (parent.inject_expectation_group = false"
        + "   AND NOT EXISTS (SELECT 1 FROM injects_expectations child WHERE "
        + childJoin
        + "     AND NOT ("
        + childSuccess
        + "))))";
  }
}
