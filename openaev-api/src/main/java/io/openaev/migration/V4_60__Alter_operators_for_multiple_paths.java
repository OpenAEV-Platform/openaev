package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_60__Alter_operators_for_multiple_paths extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // Drop old function signature if it exists (idempotent)
      stmt.execute(
          """
                    DROP FUNCTION IF EXISTS array_to_string_wrapper(anyelement, text);
                """);

      // Create or replace is inherently idempotent
      stmt.execute(
          """
          -- Version pour arrays
          CREATE OR REPLACE FUNCTION array_to_string_wrapper(a anyarray, b text)
              RETURNS TEXT AS
              $$
                  SELECT array_to_string(a, b)
              $$ LANGUAGE SQL IMMUTABLE;
          """);

      stmt.execute(
          """
          -- Version pour scalaires (VARCHAR)
          CREATE OR REPLACE FUNCTION array_to_string_wrapper(a VARCHAR, b text)
              RETURNS TEXT AS
              $$
                  SELECT a
              $$ LANGUAGE SQL IMMUTABLE;
          """);
    }
  }
}
