package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Adds a soft-delete marker on user API tokens to retain renewal history safely. */
@Component
public class V6_20260824143000000__Add_deleted_at_to_tokens extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE tokens ADD COLUMN IF NOT EXISTS token_deleted_at timestamp(0) with time zone;");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_tokens_value_active ON tokens(token_value) WHERE token_deleted_at IS NULL;");
    }
  }
}
