package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Triforce identity, Phase 1: introduces the "Location" leg of the Type+Value+Location Finding
 * identity for asset-based finding types (network/host, credential, file, domain, share,
 * computer - see finding_triforce_design.md). Today a Finding's natural key is (finding_type,
 * finding_value, finding_inject_id), so the same check re-detected by a different inject - even
 * on the exact same asset - creates a disconnected duplicate row, while the global list's distinct
 * search (FindingSpecification#distinctTypeValueWithFilter) groups by (type, value) ONLY, silently
 * merging findings that are actually on different assets into a single representative row.
 *
 * <p>finding_location_asset_id is the stable anchor that both problems will be fixed against:
 * FindingRepository#upsertFinding will fold it into the natural key (so re-detection on the same
 * asset updates the same row), and the distinct search will group by (type, value,
 * location_asset_id) instead of (type, value) (so distinct assets never merge).
 *
 * <p>Backfill scope (Phase 1): only findings currently linked to EXACTLY ONE asset in
 * findings_assets are backfilled here - this covers the overwhelming majority of existing rows.
 * Findings linked to zero assets (no Location resolvable, e.g. manually-created/text findings)
 * or to more than one asset (today's many-to-many "one finding, N assets" shape, which Phase 1
 * does not yet split into N distinct findings - that is a separate, explicit data migration
 * decision, deferred to Phase 1b) are left with finding_location_asset_id = NULL and keep today's
 * (type, value)-only grouping behavior until then.
 *
 * <p>ON DELETE SET NULL (not CASCADE): deleting the underlying asset must never cascade-delete a
 * Finding and destroy its triage/comment/archive history - that would contradict the platform's
 * "nothing triaged is ever silently lost" principle. A Finding whose location asset was deleted
 * simply falls back to being unlocated (NULL), same as a Finding that never had a resolvable
 * Location.
 */
@Component
public class V6_20260819150900000__Add_finding_location_asset extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              ALTER TABLE findings
                  ADD COLUMN IF NOT EXISTS finding_location_asset_id VARCHAR(255)
                      CONSTRAINT finding_location_asset_id_fk REFERENCES assets (asset_id) ON DELETE SET NULL;
              """);
      statement.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_findings_location_asset_id
                  ON findings (finding_location_asset_id);
              """);
      // Backfill: only findings with exactly one linked asset are unambiguous today.
      statement.execute(
          """
              UPDATE findings f
                  SET finding_location_asset_id = single_asset.asset_id
                  FROM (
                      SELECT finding_id, MIN(asset_id) AS asset_id
                      FROM findings_assets
                      GROUP BY finding_id
                      HAVING COUNT(*) = 1
                  ) AS single_asset
                  WHERE f.finding_id = single_asset.finding_id
                    AND f.finding_location_asset_id IS NULL;
              """);

      // Decision #5 (finding_triforce_design.md): historical rows that the backfill above just
      // made collide on the new (type, value, field, location) key are merged here with a
      // last-write-wins policy BEFORE the unique constraint below is added, otherwise that
      // constraint would fail to apply on any tenant with pre-existing cross-inject duplicates.
      // The "winner" of each group is the row with the most recent finding_updated_at (tie-break:
      // lowest finding_id, for determinism). Everything attached to the losing row is preserved,
      // not discarded:
      //  - finding_triages: the most recently updated triage status among the whole group wins
      //    (last human action wins, per Decision #5) and ends up attached to the winner row;
      //  - finding_triage_histories and finding_comments: always re-pointed to the winner, never
      //    dropped - both are append-only audit trails;
      //  - findings_tags/_teams/_users: re-pointed with ON CONFLICT DO NOTHING (winner may
      //    already hold some of the same links).
      // Only then are the losing finding rows deleted (cascading their now-empty leftover links).
      statement.execute(
          """
              CREATE TEMP TABLE finding_dupe_groups AS
              SELECT
                  finding_id,
                  FIRST_VALUE(finding_id) OVER (
                      PARTITION BY finding_type, finding_value, finding_field, finding_location_asset_id
                      ORDER BY finding_updated_at DESC, finding_id ASC
                  ) AS winner_id
              FROM findings
              WHERE finding_location_asset_id IS NOT NULL;
              """);
      statement.execute(
          """
              DELETE FROM finding_dupe_groups g
              WHERE NOT EXISTS (
                  SELECT 1 FROM finding_dupe_groups g2
                  WHERE g2.winner_id = g.winner_id AND g2.finding_id <> g2.winner_id
              );
              """);
      statement.execute(
          """
              CREATE TEMP TABLE finding_dupe_best_triage AS
              SELECT DISTINCT ON (g.winner_id)
                  g.winner_id,
                  t.finding_triage_id AS best_triage_id,
                  t.finding_triage_finding_id AS best_triage_owner_id
              FROM finding_dupe_groups g
              JOIN finding_triages t ON t.finding_triage_finding_id = g.finding_id
              ORDER BY g.winner_id, t.finding_triage_updated_at DESC;
              """);
      statement.execute(
          """
              UPDATE finding_triages t
                  SET finding_triage_finding_id = b.winner_id
                  FROM finding_dupe_best_triage b
                  WHERE t.finding_triage_id = b.best_triage_id
                    AND b.best_triage_owner_id <> b.winner_id
                    AND NOT EXISTS (
                        SELECT 1 FROM finding_triages t2
                        WHERE t2.finding_triage_finding_id = b.winner_id
                    );
              """);
      statement.execute(
          """
              UPDATE finding_triages t
                  SET finding_triage_status = src.finding_triage_status,
                      finding_triage_updated_at = src.finding_triage_updated_at
                  FROM finding_dupe_best_triage b
                  JOIN finding_triages src ON src.finding_triage_id = b.best_triage_id
                  WHERE t.finding_triage_finding_id = b.winner_id
                    AND b.best_triage_owner_id <> b.winner_id
                    AND src.finding_triage_updated_at > t.finding_triage_updated_at;
              """);
      statement.execute(
          """
              UPDATE finding_triage_histories h
                  SET finding_triage_history_finding_id = g.winner_id
                  FROM finding_dupe_groups g
                  WHERE h.finding_triage_history_finding_id = g.finding_id
                    AND g.finding_id <> g.winner_id;
              """);
      statement.execute(
          """
              UPDATE finding_comments c
                  SET finding_comment_finding_id = g.winner_id
                  FROM finding_dupe_groups g
                  WHERE c.finding_comment_finding_id = g.finding_id
                    AND g.finding_id <> g.winner_id;
              """);
      statement.execute(
          """
              INSERT INTO findings_tags (finding_id, tag_id)
              SELECT g.winner_id, ft.tag_id
              FROM finding_dupe_groups g JOIN findings_tags ft ON ft.finding_id = g.finding_id
              WHERE g.finding_id <> g.winner_id
              ON CONFLICT DO NOTHING;
              """);
      statement.execute(
          """
              INSERT INTO findings_teams (finding_id, team_id)
              SELECT g.winner_id, ft.team_id
              FROM finding_dupe_groups g JOIN findings_teams ft ON ft.finding_id = g.finding_id
              WHERE g.finding_id <> g.winner_id
              ON CONFLICT DO NOTHING;
              """);
      statement.execute(
          """
              INSERT INTO findings_users (finding_id, user_id)
              SELECT g.winner_id, fu.user_id
              FROM finding_dupe_groups g JOIN findings_users fu ON fu.finding_id = g.finding_id
              WHERE g.finding_id <> g.winner_id
              ON CONFLICT DO NOTHING;
              """);
      statement.execute(
          """
              DELETE FROM findings f
                  USING finding_dupe_groups g
                  WHERE f.finding_id = g.finding_id
                    AND g.finding_id <> g.winner_id;
              """);

      // Only now is it safe to enforce the new Location-aware identity going forward: partial
      // (NULLs are never considered equal by Postgres, so unlocated findings are correctly
      // exempted) unique index backing FindingRepository#upsertFinding's new conflict target.
      statement.execute(
          """
              CREATE UNIQUE INDEX IF NOT EXISTS uq_findings_location_key
                  ON findings (finding_type, finding_value, finding_field, finding_location_asset_id)
                  WHERE finding_location_asset_id IS NOT NULL;
              """);

      // Drop the legacy (finding_inject_id, finding_type, finding_value, finding_field) unique
      // constraint: Postgres enforces ALL unique constraints on a table regardless of which one
      // an "ON CONFLICT" clause names as its arbiter, so leaving this one in place would still
      // raise a hard duplicate-key error the moment the same inject re-detects the same
      // type+value+field combo on a second, different asset (a legitimate distinct Location under
      // the new identity, and exactly the case Task 1 must support). It is superseded by
      // uq_findings_location_key above for located findings; unlocated findings (Phase 1b) are
      // deliberately left with no uniqueness guarantee for now, matching their pre-Phase-1
      // behavior of never being deduplicated by this upsert path in the first place.
      statement.execute("ALTER TABLE findings DROP CONSTRAINT IF EXISTS unique_finding_constraint;");
    }
  }
}
