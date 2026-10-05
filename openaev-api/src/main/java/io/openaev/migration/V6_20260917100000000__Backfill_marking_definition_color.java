package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260917100000000__Backfill_marking_definition_color extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      if (!columnExists(statement)) {
        return;
      }

      statement.executeUpdate(
          """
          UPDATE marking_definitions
          SET marking_definition_color = CASE UPPER(marking_definition_definition)
              WHEN 'TLP:CLEAR' THEN '#E6E7E8'
              WHEN 'TLP:GREEN' THEN '#4CAF50'
              WHEN 'TLP:AMBER' THEN '#FFB300'
              WHEN 'TLP:AMBER+STRICT' THEN '#FF8F00'
              WHEN 'TLP:RED' THEN '#E53935'
              ELSE '#E6E7E8'
            END
          WHERE marking_definition_color IS NULL
             OR btrim(marking_definition_color) = ''
          """);

      if (countInvalidColors(statement) > 0) {
        throw new IllegalStateException(
            "marking_definitions contains NULL or blank marking_definition_color values after backfill");
      }

      if (isNullable(statement)) {
        statement.execute(
            "ALTER TABLE marking_definitions ALTER COLUMN marking_definition_color SET NOT NULL");
      }
    }
  }

  private boolean columnExists(Statement statement) throws SQLException {
    try (ResultSet resultSet =
        statement.executeQuery(
            """
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'marking_definitions'
              AND column_name = 'marking_definition_color'
            """)) {
      return resultSet.next();
    }
  }

  private boolean isNullable(Statement statement) throws SQLException {
    try (ResultSet resultSet =
        statement.executeQuery(
            """
            SELECT is_nullable
            FROM information_schema.columns
            WHERE table_name = 'marking_definitions'
              AND column_name = 'marking_definition_color'
            """)) {
      return resultSet.next() && "YES".equals(resultSet.getString(1));
    }
  }

  private long countInvalidColors(Statement statement) throws SQLException {
    try (ResultSet resultSet =
        statement.executeQuery(
            """
            SELECT count(*)
            FROM marking_definitions
            WHERE marking_definition_color IS NULL
               OR btrim(marking_definition_color) = ''
            """)) {
      resultSet.next();
      return resultSet.getLong(1);
    }
  }
}
