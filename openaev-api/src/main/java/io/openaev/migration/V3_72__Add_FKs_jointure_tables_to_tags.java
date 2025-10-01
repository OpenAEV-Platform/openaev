package io.openaev.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_72__Add_FKs_jointure_tables_to_tags extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      // Clean orphaned references where FK source no longer exists
      cleanOrphanedReferences(statement, "assets_tags", "asset_id", "assets");
      cleanOrphanedReferences(statement, "asset_groups_tags", "asset_group_id", "asset_groups");
      cleanOrphanedReferences(statement, "challenges_tags", "challenge_id", "challenges");
      cleanOrphanedReferences(statement, "documents_tags", "document_id", "documents");
      cleanOrphanedReferences(statement, "exercises_tags", "exercise_id", "exercises");
      cleanOrphanedReferences(statement, "injects_tags", "inject_id", "injects");
      cleanOrphanedReferences(statement, "logs_tags", "log_id", "logs");
      cleanOrphanedReferences(statement, "organizations_tags", "organization_id", "organizations");
      cleanOrphanedReferences(statement, "scenarios_tags", "scenario_id", "scenarios");
      cleanOrphanedReferences(statement, "teams_tags", "team_id", "teams");
      cleanOrphanedReferences(statement, "users_tags", "user_id", "users");

      // Clean orphaned tag references if tag_id is not in tags table
      cleanOrphanedReferences(statement, "assets_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "asset_groups_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "challenges_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "documents_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "exercises_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "injects_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "logs_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "organizations_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "scenarios_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "teams_tags", "tag_id", "tags");
      cleanOrphanedReferences(statement, "users_tags", "tag_id", "tags");

      // Recreate foreign key constraints for primary entities
      recreateForeignConstraints(statement, "asset_groups_tags", "asset_group_id", "asset_groups");
      recreateForeignConstraints(statement, "assets_tags", "asset_id", "assets");
      recreateForeignConstraints(statement, "challenges_tags", "challenge_id", "challenges");
      recreateForeignConstraints(statement, "documents_tags", "document_id", "documents");
      recreateForeignConstraints(statement, "exercises_tags", "exercise_id", "exercises");
      recreateForeignConstraints(statement, "injects_tags", "inject_id", "injects");
      recreateForeignConstraints(statement, "logs_tags", "log_id", "logs");
      recreateForeignConstraints(
          statement, "organizations_tags", "organization_id", "organizations");
      recreateForeignConstraints(statement, "scenarios_tags", "scenario_id", "scenarios");
      recreateForeignConstraints(statement, "teams_tags", "team_id", "teams");
      recreateForeignConstraints(statement, "users_tags", "user_id", "users");

      // Recreate foreign key constraints for tag_id in all join tables
      recreateForeignConstraints(statement, "assets_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "asset_groups_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "challenges_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "documents_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "exercises_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "injects_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "logs_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "organizations_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "scenarios_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "teams_tags", "tag_id", "tags");
      recreateForeignConstraints(statement, "users_tags", "tag_id", "tags");
    }
  }

  /** Deletes orphaned rows from a join table where the referenced key no longer exists. */
  private static void cleanOrphanedReferences(
      Statement statement, String table, String key, String referenceTable) throws SQLException {
    String sql =
        String.format(
            "DELETE FROM %s WHERE %s NOT IN (SELECT %s FROM %s);", table, key, key, referenceTable);
    statement.executeUpdate(sql);
  }

  /** Recreates foreign key constraints with ON DELETE CASCADE. */
  private static void recreateForeignConstraints(
      Statement statement, String table, String key, String referenceTable) throws SQLException {
    // Drop existing foreign key constraint (if exists)
    String dropConstraintSQL =
        String.format("ALTER TABLE %s DROP CONSTRAINT IF EXISTS %s_fk;", table, key);
    statement.executeUpdate(dropConstraintSQL);

    // Add new foreign key constraint with ON DELETE CASCADE
    String addConstraintSQL =
        String.format(
            "ALTER TABLE %s ADD CONSTRAINT %s_fk FOREIGN KEY (%s) REFERENCES %s(%s) ON DELETE CASCADE;",
            table, key, key, referenceTable, key);
    statement.executeUpdate(addConstraintSQL);
  }
}
