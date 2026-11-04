package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code attackpath_execution_asset_id}: the one asset an attack-path execution row is about,
 * so the row can be marked through {@code assets} like every other derived table (#8555).
 *
 * <p>A row stores a source and a target asset id, but real ingestion never writes two different
 * assets in one row: an agent row has the agent's asset as source and either the same asset or a
 * discovered host (no asset id) as target, and an injector row has no source asset and an asset as
 * target. The column therefore holds the source asset of an agent row, falling back to its target
 * asset, and the target asset otherwise.
 *
 * <p>The {@code CASE} on the source kind is not optional: a team row stores the <b>team id</b> in
 * {@code source_asset_id}, and a link on that raw column would hide every team to player row from
 * every reader, because a team id matches no asset.
 *
 * <p>The column is a plain one, not a generated one, so that it can carry {@code ON DELETE SET
 * NULL}: attack-path rows are frozen copies without a foreign key on their asset, and must stay
 * visible once the asset is deleted. A NULL link reads as "points to no asset", hence visible, so
 * the marking core needs no special case. A {@code BEFORE INSERT OR UPDATE OF} trigger fills the
 * column, so no writer (ingestion, seeders, raw SQL) has to know about it. It stores the asset id
 * only when that asset exists: an id with no asset behind it (a seeded demo host, an asset deleted
 * before this migration) gives NULL instead of breaking the foreign key. The {@code SET NULL}
 * update of the foreign key touches only the link column, which the trigger does not watch, so it
 * does not put the id back.
 *
 * <p>Additive and idempotent. The backfill is one statement, so the table stays locked for its
 * duration. No Elasticsearch-indexed entity is touched, so no {@code indexing_status} reset.
 */
@Component
public class V6_20261103100000000__Add_attackpath_execution_asset_id extends BaseJavaMigration {

  /** The asset the row is about, from the columns of {@code row} (a table alias or NEW). */
  private static String assetOf(String row) {
    return "CASE WHEN "
        + row
        + ".attackpath_execution_source_kind = 'AGENT' "
        + "THEN COALESCE("
        + row
        + ".attackpath_execution_source_asset_id, "
        + row
        + ".attackpath_execution_target_asset_id) "
        + "ELSE "
        + row
        + ".attackpath_execution_target_asset_id END";
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_execution ADD COLUMN IF NOT EXISTS "
              + "attackpath_execution_asset_id VARCHAR(255);");
      // Backfill before the foreign key, and only for assets that exist, so it validates.
      statement.execute(
          "UPDATE attackpath_execution ae SET attackpath_execution_asset_id = a.asset_id "
              + "FROM assets a WHERE a.asset_id = "
              + assetOf("ae")
              + " AND ae.attackpath_execution_asset_id IS DISTINCT FROM a.asset_id;");
      statement.execute(
          "ALTER TABLE attackpath_execution "
              + "DROP CONSTRAINT IF EXISTS fk_attackpath_execution_asset;");
      statement.execute(
          "ALTER TABLE attackpath_execution ADD CONSTRAINT fk_attackpath_execution_asset "
              + "FOREIGN KEY (attackpath_execution_asset_id) REFERENCES assets (asset_id) "
              + "ON DELETE SET NULL;");
      // The foreign key needs its index: each asset delete looks the rows up by this column.
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_attackpath_execution_asset "
              + "ON attackpath_execution (attackpath_execution_asset_id);");
      statement.execute(
          "CREATE OR REPLACE FUNCTION attackpath_execution_set_asset_id() RETURNS trigger AS $$ "
              + "BEGIN "
              + "SELECT a.asset_id INTO NEW.attackpath_execution_asset_id FROM assets a "
              + "WHERE a.asset_id = "
              + assetOf("NEW")
              + "; RETURN NEW; END; $$ LANGUAGE plpgsql;");
      statement.execute(
          "DROP TRIGGER IF EXISTS attackpath_execution_asset_id_trigger "
              + "ON attackpath_execution;");
      statement.execute(
          "CREATE TRIGGER attackpath_execution_asset_id_trigger "
              + "BEFORE INSERT OR UPDATE OF attackpath_execution_source_kind, "
              + "attackpath_execution_source_asset_id, attackpath_execution_target_asset_id "
              + "ON attackpath_execution FOR EACH ROW "
              + "EXECUTE FUNCTION attackpath_execution_set_asset_id();");
    }
  }
}
