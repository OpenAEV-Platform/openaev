package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repairs parent detection/prevention expectations clobbered by collectors of a mismatched security
 * platform type.
 *
 * <p>Bug: the AI-defense feed ({@code GET /api/injects/expectations/ai/{sourceId}}, backed by
 * {@code findAgentlessExpectationsNotFilledForSource}) returned every expectation with {@code
 * agent_id IS NULL AND asset_id IS NOT NULL} - which is also the exact shape of a PARENT
 * expectation of an endpoint with agents - and ignored {@code
 * inject_expectation_expected_security_platforms}. An LLM firewall collector (XTM One) therefore
 * received EDR-only endpoint parents, found no matching AI security event, and after its expiration
 * window wrote {@code score 0 / "Not Detected"} directly on the parent rows. The write path
 * recomputed the parent score from its own results only, clobbering the verdict rolled up from the
 * agents (e.g. Microsoft Defender green), and the failure then propagated to the asset group rows.
 * The bogus result was additionally invisible in the UI, whose asset view is built from the agents'
 * results.
 *
 * <p>The feed and the write path are fixed in code; this migration repairs the corrupted rows:
 *
 * <ol>
 *   <li>strips, from asset-level parents that have agent children, direct results written by a
 *       collector whose security platform type is not among the expectation's expected types;
 *   <li>restores the score of asset-level parents whose agent children verdict is green (any child
 *       for group expectations, all children for non-group);
 *   <li>restores the score of asset-group parents whose (possibly just repaired) asset children
 *       verdict is green.
 * </ol>
 *
 * <p>Every repaired row gets {@code inject_expectation_updated_at = now()} so the incremental
 * Elasticsearch engine (which cursors on {@code updated_at}) re-feeds the documents and the
 * ES-backed statistics converge on their own within its polling interval.
 *
 * <p>Idempotent: step 1 only matches rows still carrying a mismatched result, steps 2 and 3 only
 * match rows whose score is still below the expected score; re-runs match nothing.
 */
@Component
public
class V6_20260727080000000__Repair_parent_expectations_clobbered_by_mismatched_collector_results
    extends BaseJavaMigration {

  /** An answered child that reached its expected score. */
  private static final String CHILD_SUCCESS =
      "child.inject_expectation_score IS NOT NULL"
          + " AND child.inject_expectation_score >= child.inject_expectation_expected_score";

  /**
   * A result element written by a collector whose security platform type is NOT among the
   * expectation's expected platform types. Collectors without a security platform (e.g. the
   * expectations expiration manager) never match: only positively identified mismatches are
   * stripped.
   */
  private static final String MISMATCHED_RESULT_SOURCE =
      "EXISTS (SELECT 1 FROM collectors c"
          + " JOIN assets sp ON sp.asset_id = c.collector_security_platform"
          + " WHERE c.collector_id = elem->>'sourceId'"
          + " AND sp.security_platform_type IS NOT NULL"
          + " AND NOT jsonb_exists("
          + "parent.inject_expectation_expected_security_platforms::jsonb,"
          + " sp.security_platform_type))";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1) Strip mismatched-platform direct results from asset-level parents with agent children
      statement.execute(
          "UPDATE injects_expectations parent SET"
              + " inject_expectation_results = COALESCE((SELECT jsonb_agg(elem)"
              + "   FROM jsonb_array_elements(parent.inject_expectation_results::jsonb) elem"
              + "   WHERE NOT "
              + MISMATCHED_RESULT_SOURCE
              + "), '[]'::jsonb)::json,"
              + " inject_expectation_updated_at = now()"
              + " WHERE parent.agent_id IS NULL AND parent.asset_id IS NOT NULL"
              + " AND parent.inject_expectation_type IN ('DETECTION', 'PREVENTION')"
              + " AND parent.inject_expectation_expected_security_platforms IS NOT NULL"
              + " AND jsonb_typeof(parent.inject_expectation_expected_security_platforms::jsonb)"
              + "   = 'array'"
              + " AND jsonb_array_length("
              + "parent.inject_expectation_expected_security_platforms::jsonb) > 0"
              + " AND parent.inject_expectation_results IS NOT NULL"
              + " AND jsonb_typeof(parent.inject_expectation_results::jsonb) = 'array'"
              + " AND EXISTS (SELECT 1 FROM injects_expectations child"
              + "   WHERE child.inject_id = parent.inject_id"
              + "   AND child.asset_id = parent.asset_id"
              + "   AND child.inject_expectation_type = parent.inject_expectation_type"
              + "   AND child.agent_id IS NOT NULL)"
              + " AND EXISTS (SELECT 1"
              + "   FROM jsonb_array_elements(parent.inject_expectation_results::jsonb) elem"
              + "   WHERE "
              + MISMATCHED_RESULT_SOURCE
              + ")");
      // 2) Asset-level parents: restore the score when their agent children verdict is green
      statement.execute(
          repairScoreSql(
              /* parentScope= */ "parent.asset_id IS NOT NULL AND parent.agent_id IS NULL",
              /* childJoin= */ "child.inject_id = parent.inject_id"
                  + " AND child.inject_expectation_type = parent.inject_expectation_type"
                  + " AND child.asset_id = parent.asset_id"
                  + " AND child.agent_id IS NOT NULL"));
      // 3) Asset-group parents: restore the score when their (possibly just repaired) asset
      // children verdict is green
      statement.execute(
          repairScoreSql(
              /* parentScope= */ "parent.asset_group_id IS NOT NULL AND parent.asset_id IS NULL"
                  + " AND parent.agent_id IS NULL",
              /* childJoin= */ "child.inject_id = parent.inject_id"
                  + " AND child.inject_expectation_type = parent.inject_expectation_type"
                  + " AND child.asset_group_id = parent.asset_group_id"
                  + " AND child.asset_id IS NOT NULL"
                  + " AND child.agent_id IS NULL"));
    }
  }

  private static String repairScoreSql(String parentScope, String childJoin) {
    return "UPDATE injects_expectations parent SET"
        + " inject_expectation_score = parent.inject_expectation_expected_score,"
        + " inject_expectation_updated_at = now()"
        + " WHERE "
        + parentScope
        + " AND parent.inject_expectation_type IN ('DETECTION', 'PREVENTION')"
        + " AND parent.inject_expectation_expected_score IS NOT NULL"
        + " AND parent.inject_expectation_score IS NOT NULL"
        + " AND parent.inject_expectation_score < parent.inject_expectation_expected_score"
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
        + CHILD_SUCCESS
        + "))"
        + "  OR (parent.inject_expectation_group = false"
        + "   AND NOT EXISTS (SELECT 1 FROM injects_expectations child WHERE "
        + childJoin
        + "     AND NOT ("
        + CHILD_SUCCESS
        + "))))";
  }
}
