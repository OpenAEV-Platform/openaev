package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds recurrence scheduling columns to injects so atomic testings can be relaunched on a schedule,
 * mirroring the scenario recurrence model. Additive, idempotent (IF NOT EXISTS), metadata-only
 * column additions (constant NULL default) - lock-light and safe to auto-apply.
 */
@Component
public class V6_20260725220000000__Add_inject_recurrence extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_recurrence varchar(256);
          ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_recurrence_start timestamp;
          ALTER TABLE injects ADD COLUMN IF NOT EXISTS inject_recurrence_end timestamp;
          """);
    }
  }
}

// -- ROLLBACK --
// ALTER TABLE injects DROP COLUMN IF EXISTS inject_recurrence;
// ALTER TABLE injects DROP COLUMN IF EXISTS inject_recurrence_start;
// ALTER TABLE injects DROP COLUMN IF EXISTS inject_recurrence_end;
