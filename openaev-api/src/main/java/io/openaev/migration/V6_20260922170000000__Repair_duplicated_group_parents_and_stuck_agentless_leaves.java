package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repairs the technical expectations created by the behavior-based initialization between the
 * wiring of the behaviors (2026-09-08, shipped in 3.260917.1) and the fix in {@code
 * AbstractTechnicalBehavior}.
 *
 * <p>Two regressions corrupted the rows written at inject execution time:
 *
 * <ol>
 *   <li>the asset-group parent expectation was built inside the per-asset loop, so a group of N
 *       assets got N identical parent rows per (inject, type, form expectation) instead of one -
 *       every collector update then propagated to all of them and the UI rendered N cards;
 *   <li>pending per-collector placeholder rows were seeded on AGENTLESS leaves (endpoints scanned
 *       by an assessment injector such as Nuclei, AI targets). The single direct verdict written on
 *       such a row never completes it - {@code computeScore} waits for every seeded source - and
 *       the expiration manager skips agentless rows that already carry a result, so the row stays
 *       pending forever ("Not vulnerable" from Nuclei next to an empty "Expectations Vulnerability
 *       Manager" placeholder, score null, simulation score unknown).
 * </ol>
 *
 * <p>The write path is fixed in code; this migration repairs the rows already written:
 *
 * <ol>
 *   <li>deletes the duplicated asset-group parents, keeping the oldest row of every (inject, type,
 *       asset group, name, description, expected score, group flag) tuple - the duplicates are
 *       clones of the same template, so every copy carries the same verdict. Only rows created
 *       since the regression landed are considered. Signatures and traces cascade on delete;
 *   <li>concludes the stuck agentless leaves: the empty placeholder rows are stripped from the
 *       results and the score is recomputed from the genuine verdicts exactly like {@code
 *       ExpectationResultBuilder.computeScore} (highest non-null result score). Asset rows that own
 *       agent children are parents, not leaves, and are left alone. Asset-group parents above a
 *       repaired leaf carry no result of their own, so the expiration manager rolls them up from
 *       the repaired children on its next run.
 * </ol>
 *
 * <p>Every repaired row gets {@code inject_expectation_updated_at = now()} so the incremental
 * search engine (which cursors on {@code updated_at}) re-feeds the documents.
 *
 * <p>Idempotent: step 1 only matches tuples that still have more than one row, step 2 only matches
 * unscored rows still carrying an empty placeholder next to a genuine verdict; re-runs match
 * nothing.
 */
@Component
public class V6_20260922170000000__Repair_duplicated_group_parents_and_stuck_agentless_leaves
    extends BaseJavaMigration {

  /** The behavior-based initialization was wired on main on this day (#5384). */
  private static final String REGRESSION_LANDED_AT = "2026-09-08";

  /** Asset-group parent rows: asset group set, no asset, no agent. */
  private static String groupParent(String alias) {
    return alias
        + ".asset_group_id IS NOT NULL AND "
        + alias
        + ".asset_id IS NULL AND "
        + alias
        + ".agent_id IS NULL";
  }

  /** A result element that carries a genuine verdict (non-empty result label). */
  private static final String ANSWERED_RESULT = "COALESCE(elem->>'result', '') <> ''";

  /** A pending placeholder element (no result label yet). */
  private static final String EMPTY_RESULT = "COALESCE(elem->>'result', '') = ''";

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1) Duplicated asset-group parents: keep the oldest row of each tuple, delete the others.
      // Row-wise comparison on (created_at, id) makes the survivor unique even when two clones
      // share the same creation timestamp.
      statement.execute(
          "DELETE FROM injects_expectations dup"
              + " USING injects_expectations keep"
              + " WHERE "
              + groupParent("dup")
              + " AND "
              + groupParent("keep")
              + " AND dup.inject_expectation_created_at >= '"
              + REGRESSION_LANDED_AT
              + "'"
              + " AND keep.inject_id = dup.inject_id"
              + " AND keep.inject_expectation_type = dup.inject_expectation_type"
              + " AND keep.asset_group_id = dup.asset_group_id"
              + " AND COALESCE(keep.inject_expectation_name, '')"
              + "   = COALESCE(dup.inject_expectation_name, '')"
              + " AND COALESCE(keep.inject_expectation_description, '')"
              + "   = COALESCE(dup.inject_expectation_description, '')"
              + " AND keep.inject_expectation_expected_score"
              + "   IS NOT DISTINCT FROM dup.inject_expectation_expected_score"
              + " AND keep.inject_expectation_group = dup.inject_expectation_group"
              + " AND (keep.inject_expectation_created_at, keep.inject_expectation_id)"
              + "   < (dup.inject_expectation_created_at, dup.inject_expectation_id)");

      // 2) Stuck agentless leaves: strip the empty placeholders, conclude from the genuine verdicts
      // (WITH ORDINALITY + ORDER BY: jsonb_agg alone does not guarantee the original array order)
      statement.execute(
          "UPDATE injects_expectations leaf SET"
              + " inject_expectation_results = COALESCE((SELECT jsonb_agg(elem ORDER BY ord)"
              + "   FROM jsonb_array_elements(leaf.inject_expectation_results::jsonb)"
              + "     WITH ORDINALITY AS results(elem, ord)"
              + "   WHERE "
              + ANSWERED_RESULT
              + "), '[]'::jsonb)::json,"
              + " inject_expectation_score = (SELECT MAX((elem->>'score')::numeric)"
              + "   FROM jsonb_array_elements(leaf.inject_expectation_results::jsonb) elem"
              + "   WHERE "
              + ANSWERED_RESULT
              + "   AND elem->>'score' IS NOT NULL),"
              + " inject_expectation_updated_at = now()"
              + " WHERE leaf.inject_expectation_type IN ('DETECTION', 'PREVENTION', 'VULNERABILITY')"
              + " AND leaf.agent_id IS NULL AND leaf.asset_id IS NOT NULL"
              + " AND leaf.inject_expectation_score IS NULL"
              + " AND leaf.inject_expectation_results IS NOT NULL"
              + " AND jsonb_typeof(leaf.inject_expectation_results::jsonb) = 'array'"
              + " AND EXISTS (SELECT 1"
              + "   FROM jsonb_array_elements(leaf.inject_expectation_results::jsonb) elem"
              + "   WHERE "
              + EMPTY_RESULT
              + ")"
              + " AND EXISTS (SELECT 1"
              + "   FROM jsonb_array_elements(leaf.inject_expectation_results::jsonb) elem"
              + "   WHERE "
              + ANSWERED_RESULT
              + "   AND elem->>'score' IS NOT NULL)"
              + " AND NOT EXISTS (SELECT 1 FROM injects_expectations child"
              + "   WHERE child.inject_id = leaf.inject_id"
              + "   AND child.inject_expectation_type = leaf.inject_expectation_type"
              + "   AND child.asset_id = leaf.asset_id"
              + "   AND child.agent_id IS NOT NULL)");
    }
  }
}
