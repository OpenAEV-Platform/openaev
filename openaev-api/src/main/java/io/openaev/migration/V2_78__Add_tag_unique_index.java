package io.openaev.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V2_78__Add_tag_unique_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      // Create temporary table for uniq tags
      statement.executeUpdate(
          """
            CREATE TABLE uniq_tags AS
            SELECT MIN(tag_id) AS uniq_id, tag_name
            FROM tags
            GROUP BY tag_name
            HAVING COUNT(*) > 1;
          """);

      // Deduplicate tag in other tables
      deduplicateInTable(statement, "asset_groups_tags", "asset_groups_tags_pkey");
      deduplicateInTable(statement, "assets_tags", "assets_tags_pkey");
      deduplicateInTable(statement, "challenges_tags", "challenges_tags_pkey");
      deduplicateInTable(statement, "documents_tags", "documents_tags_pkey");
      deduplicateInTable(statement, "exercises_tags", "exercises_tags_pkey");
      deduplicateInTable(statement, "injects_tags", "injects_tags_pkey");
      deduplicateInTable(statement, "logs_tags", "logs_tags_pkey");
      deduplicateInTable(statement, "organizations_tags", "organizations_tags_pkey");
      deduplicateInTable(statement, "scenarios_tags", "scenarios_tags_pkey");
      deduplicateInTable(statement, "teams_tags", "teams_tags_pkey");
      deduplicateInTable(statement, "users_tags", "users_tags_pkey");

      // Remove duplicate tags in tags table
      statement.executeUpdate(
          """
          DELETE FROM tags
          WHERE tags.tag_name IN (SELECT uniq_tags.tag_name FROM uniq_tags)
          AND NOT EXISTS (
             SELECT 1
             FROM uniq_tags AS ut
             WHERE tags.tag_id = ut.uniq_id
         );
          """);

      // Recreate primary constraint
      recreatePrimaryConstraint(
          statement, "asset_groups_tags", "asset_groups_tags_pkey", "asset_group_id");
      recreatePrimaryConstraint(statement, "assets_tags", "assets_tags_pkey", "asset_id");
      recreatePrimaryConstraint(
          statement, "challenges_tags", "challenges_tags_pkey", "challenge_id");
      recreatePrimaryConstraint(statement, "documents_tags", "documents_tags_pkey", "document_id");
      recreatePrimaryConstraint(statement, "exercises_tags", "exercises_tags_pkey", "exercise_id");
      recreatePrimaryConstraint(statement, "injects_tags", "injects_tags_pkey", "inject_id");
      recreatePrimaryConstraint(statement, "logs_tags", "logs_tags_pkey", "log_id");
      recreatePrimaryConstraint(
          statement, "organizations_tags", "organizations_tags_pkey", "organization_id");
      recreatePrimaryConstraint(statement, "scenarios_tags", "scenarios_tags_pkey", "scenario_id");
      recreatePrimaryConstraint(statement, "teams_tags", "teams_tags_pkey", "team_id");
      recreatePrimaryConstraint(statement, "users_tags", "users_tags_pkey", "user_id");

      // Add unicity
      statement.executeUpdate(
          """
            CREATE UNIQUE INDEX IF NOT EXISTS tag_name_unique ON tags (tag_name);
          """);

      // Remove temporary table
      statement.executeUpdate(
          """
            DROP TABLE IF EXISTS uniq_tags;
          """);
    }
  }

  private static void deduplicateInTable(Statement statement, String table, String constraintKey)
      throws SQLException {
    // Remove primary key
    statement.executeUpdate(
        """
        ALTER TABLE """
            + " ".concat(table).concat(" ")
            + """
            DROP CONSTRAINT  """
            + " ".concat(constraintKey).concat(" ")
            + """
        """);
    // Set uniq tag
    statement.executeUpdate(
        """
        UPDATE """
            + " ".concat(table).concat(" ")
            + """
        tableName
        SET tag_id = uniq_tag.uniq_id
        FROM uniq_tags uniq_tag
        JOIN tags t ON uniq_tag.tag_name = t.tag_name
        WHERE tableName.tag_id = t.tag_id;
        """);
    // Remove duplicate line by create a new table with distinct
    statement.executeUpdate(
        """
        CREATE TABLE """
            + " ".concat(table).concat("_temp")
            + """
            AS
          SELECT DISTINCT * FROM """
            + " ".concat(table).concat(" ")
            + """
        """);
    statement.executeUpdate(
        """
        DROP TABLE """
            + " ".concat(table).concat(" ")
            + """
        """);
    statement.executeUpdate(
        """
        ALTER TABLE """
            + " ".concat(table).concat("_temp")
            + """
          RENAME
        TO """
            + " ".concat(table).concat(" ")
            + """
        """);
  }

  private static void recreatePrimaryConstraint(
      Statement statement, String table, String constraintKey, String key) throws SQLException {
    // Add primary key
    statement.executeUpdate(
        """
        ALTER TABLE """
            + " ".concat(table).concat(" ")
            + """
          ADD CONSTRAINT """
            + " ".concat(constraintKey).concat(" ")
            + """
          PRIMARY KEY (
            """
            + " ".concat(key).concat(" ")
            + """
            , tag_id
            );
        """);
  }
}
