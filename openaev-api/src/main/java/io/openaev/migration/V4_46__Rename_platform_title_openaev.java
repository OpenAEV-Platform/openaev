package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_46__Rename_platform_title_openaev extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      String[][] patterns = {
        {"OPENBAS", "OPENAEV"},
        {"OpenBAS", "OpenAEV"},
        {"openbas", "openaev"},
        {"Openbas", "Openaev"},
        {"Breach & Attack Simulation", "Adversarial Exposure Validation"}
      };

      // Build nested REGEXP_REPLACE calls
      StringBuilder sql = new StringBuilder("UPDATE parameters SET parameter_value = ");

      // Start with the innermost value
      String current = "parameter_value";

      // Wrap each pattern in a REGEXP_REPLACE
      for (String[] pattern : patterns) {
        current =
            String.format("REGEXP_REPLACE(%s, '%s', '%s', 'g')", current, pattern[0], pattern[1]);
      }

      sql.append(current);
      sql.append(" WHERE parameter_key = 'platform_name' AND parameter_value ~* 'openbas'");

      statement.executeUpdate(sql.toString());
    }
  }
}
