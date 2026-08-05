package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds {@code attackpath_finding_is_finding} so the attack-path snapshot can distinguish a real
 * finding ({@code true}) from an output-only value ({@code false}) produced by the chaining but not
 * persisted as a {@link io.openaev.database.model.Finding} (ADR-004). Defaults to {@code true} so
 * pre-existing and seed rows keep their current meaning. Additive and idempotent.
 */
@Component
public class V6_20260803000000000__Add_attackpath_finding_is_finding extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE attackpath_finding ADD COLUMN IF NOT EXISTS"
              + " attackpath_finding_is_finding BOOLEAN NOT NULL DEFAULT true;");
    }
  }
}
