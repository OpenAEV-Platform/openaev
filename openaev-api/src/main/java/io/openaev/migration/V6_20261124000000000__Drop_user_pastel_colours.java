package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Drops the "Pastel colours" user preference added by V6_20261123030000000:
 * the @filigran/design-system 1.13 Chip has one style (fill and 30% hairline), so the preference
 * has nothing left to switch. Idempotent.
 */
@Component
public class V6_20261124000000000__Drop_user_pastel_colours extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE users DROP COLUMN IF EXISTS user_pastel_colours");
    }
  }
}
