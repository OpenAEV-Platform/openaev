package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Deduplicates {@code kill_chain_phases} on the upsert natural key {@code (phase_kill_chain_name,
 * phase_shortname, tenant_id)} and enforces it with a unique constraint.
 *
 * <p>The collector upsert endpoint resolves existing phases by (kill chain name, short name), but
 * until now the database only enforced uniqueness on (phase_name, ...) and (phase_stix_id, ...).
 * Concurrent collector upserts right after startup (MITRE Enterprise / Mobile / ICS calling {@code
 * /api/kill_chain_phases/upsert} at the same time) could insert the same phase twice, producing
 * either {@code kill_chain_phases_stix_id_tenant_unique} violations (seen in production for the new
 * ATT&CK "Stealth" tactic) or duplicate rows that make the natural-key lookup non-deterministic.
 *
 * <p>Steps, all idempotent:
 *
 * <ol>
 *   <li>Repoint {@code attack_patterns_kill_chain_phases} links from duplicate phases to the oldest
 *       surviving row (insert-then-delete with ON CONFLICT DO NOTHING, so links that already exist
 *       on the keeper are simply collapsed).
 *   <li>Delete the duplicate phase rows, keeping the oldest per natural key.
 *   <li>Add the unique constraint on the natural key.
 * </ol>
 */
@Component
public class V6_20260722080000000__Dedupe_kill_chain_phases_natural_key extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Map every duplicate phase to the oldest row sharing its natural key.
      statement.execute(
          """
          CREATE TEMPORARY TABLE tmp_kcp_duplicates ON COMMIT DROP AS
          SELECT phase_id AS duplicate_id,
                 FIRST_VALUE(phase_id) OVER (
                   PARTITION BY phase_kill_chain_name, phase_shortname, tenant_id
                   ORDER BY phase_created_at ASC, phase_id ASC
                 ) AS keeper_id
          FROM kill_chain_phases;
          """);
      statement.execute(
          """
          DELETE FROM tmp_kcp_duplicates WHERE duplicate_id = keeper_id;
          """);
      // Repoint attack pattern links as insert-then-delete rather than UPDATE: an in-place
      // UPDATE would break the (attack_pattern_id, phase_id) primary key whenever an attack
      // pattern links to the keeper already, or to several duplicates of the same keeper.
      // ON CONFLICT DO NOTHING collapses all those cases onto the single surviving link.
      statement.execute(
          """
          INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id)
          SELECT DISTINCT apkcp.attack_pattern_id, dup.keeper_id
          FROM attack_patterns_kill_chain_phases apkcp
          JOIN tmp_kcp_duplicates dup ON apkcp.phase_id = dup.duplicate_id
          ON CONFLICT DO NOTHING;
          """);
      statement.execute(
          """
          DELETE FROM attack_patterns_kill_chain_phases apkcp
          USING tmp_kcp_duplicates dup
          WHERE apkcp.phase_id = dup.duplicate_id;
          """);
      statement.execute(
          """
          DELETE FROM kill_chain_phases kcp
          USING tmp_kcp_duplicates dup
          WHERE kcp.phase_id = dup.duplicate_id;
          """);
      // Enforce the natural key used by the upsert lookup. Guarded for idempotency:
      // ADD CONSTRAINT has no IF NOT EXISTS in PostgreSQL.
      statement.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'kill_chain_phases_shortname_tenant_unique'
            ) THEN
              ALTER TABLE kill_chain_phases
                ADD CONSTRAINT kill_chain_phases_shortname_tenant_unique
                UNIQUE (phase_kill_chain_name, phase_shortname, tenant_id);
            END IF;
          END $$;
          """);
    }
  }
}
