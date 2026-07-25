package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code attackpath_finding_field} (the source finding's contract-output key) so a copied
 * finding keeps its full source identity (value, type, field), plus a partial unique index on
 * (simulation, type, field, value, endpoint_key) that lets the copy upsert idempotently (ON
 * CONFLICT DO NOTHING). The column is nullable: pre-existing and seed rows carry no field, so the
 * index is scoped to rows that have one ({@code WHERE field IS NOT NULL}) - i.e. the copied rows.
 * This keeps the seed, which writes several findings of the same (type, value) on an endpoint with
 * no field, unconstrained. Additive and idempotent.
 */
@Component
public class V6_20260725120000000__Add_attackpath_finding_field extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_finding ADD COLUMN IF NOT EXISTS attackpath_finding_field text;");
      statement.execute("DROP INDEX IF EXISTS uq_ap_find_natural_key;");
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_ap_find_natural_key ON attackpath_finding ("
              + "attackpath_finding_simulation_id, attackpath_finding_type, attackpath_finding_field, "
              + "attackpath_finding_value, attackpath_finding_endpoint_key) "
              + "WHERE attackpath_finding_field IS NOT NULL;");
    }
  }
}
