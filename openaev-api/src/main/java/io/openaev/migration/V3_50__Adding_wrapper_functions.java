package io.openaev.migration;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_50__Adding_wrapper_functions extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws SQLException {
    Statement migrator = context.getConnection().createStatement();
    migrator.execute(
        """
                CREATE OR REPLACE FUNCTION array_to_string_wrapper(a anyelement, b text)
                      RETURNS TEXT AS
                      $$
                  SELECT array_to_string(a, b)
                      $$ LANGUAGE SQL;
                """);
    migrator.execute(
        """
                CREATE OR REPLACE FUNCTION array_position_wrapper(a anyelement, b text)
                      RETURNS INT AS
                      $$
                  SELECT array_position(a, b)
                      $$ LANGUAGE SQL;
                    """);
  }
}
