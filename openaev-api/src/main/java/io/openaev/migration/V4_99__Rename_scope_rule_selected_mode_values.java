package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_99__Rename_scope_rule_selected_mode_values extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      stmt.execute("ALTER TYPE scope_rule_selected_mode RENAME VALUE 'WHITELIST' TO 'ALLOWLIST';");
      stmt.execute("ALTER TYPE scope_rule_selected_mode RENAME VALUE 'BLACKLIST' TO 'DENYLIST';");
    }
  }
}
