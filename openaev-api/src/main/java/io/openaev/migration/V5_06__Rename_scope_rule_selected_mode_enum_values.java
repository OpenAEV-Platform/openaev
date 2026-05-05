package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V5_06__Rename_scope_rule_selected_mode_enum_values extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              DO $$
              BEGIN
                IF EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'WHITELIST'
                           AND enumtypid = 'scope_rule_selected_mode'::regtype) THEN
                  ALTER TYPE scope_rule_selected_mode RENAME VALUE 'WHITELIST' TO 'ALLOWLIST';
                END IF;
                IF EXISTS (SELECT 1 FROM pg_enum WHERE enumlabel = 'BLACKLIST'
                           AND enumtypid = 'scope_rule_selected_mode'::regtype) THEN
                  ALTER TYPE scope_rule_selected_mode RENAME VALUE 'BLACKLIST' TO 'DENYLIST';
                END IF;
              END $$;
              """);
    }
  }
}
