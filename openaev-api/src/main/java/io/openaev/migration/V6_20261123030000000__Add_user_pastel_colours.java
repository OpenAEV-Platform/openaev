package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * The "Pastel colours" user preference, shared by every XTM Platform module. Nullable on purpose:
 * an unset value reads as pastel, the OpenAEV default, so no existing user is flipped.
 */
@Component
public class V6_20261123030000000__Add_user_pastel_colours extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS user_pastel_colours BOOLEAN");
    }
  }
}
