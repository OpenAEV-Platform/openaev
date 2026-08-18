package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Normalises payload JSON list columns so they are never {@code null}.
 *
 * <p>For both {@code payload_arguments} and {@code payload_prerequisites}, an empty list and a
 * {@code null} value carry the same semantics, while {@code null} triggers runtime null-handling
 * failures on read paths. This migration backfills existing {@code null} rows to empty arrays, then
 * enforces the invariant with defaults and {@code NOT NULL} constraints.
 */
@Component
public class V6_20260818100000000__Backfill_null_payload_arguments extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1. Backfill existing NULL rows with an empty JSON array.
      statement.execute(
          "UPDATE payloads SET payload_arguments = '[]'::json WHERE payload_arguments IS NULL;");

      // 2. Make an empty array the default so future inserts can never omit it.
      statement.execute(
          "ALTER TABLE payloads ALTER COLUMN payload_arguments SET DEFAULT '[]'::json;");

      // 3. Backfill existing NULL prerequisites with an empty JSON array.
      statement.execute(
          "UPDATE payloads SET payload_prerequisites = '[]'::json WHERE payload_prerequisites IS NULL;");

      // 4. Make an empty array the default for prerequisites too.
      statement.execute(
          "ALTER TABLE payloads ALTER COLUMN payload_prerequisites SET DEFAULT '[]'::json;");

      // 5. Enforce the invariant at the database level.
      statement.execute("ALTER TABLE payloads ALTER COLUMN payload_arguments SET NOT NULL;");
      statement.execute("ALTER TABLE payloads ALTER COLUMN payload_prerequisites SET NOT NULL;");
    }
  }
}
