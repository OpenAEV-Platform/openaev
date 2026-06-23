package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds pg_trgm GIN trigram indexes on the hottest text-search columns.
 *
 * <p>The platform's text search builds {@code LOWER(column) LIKE '%term%'} predicates
 * (OperationUtilsJpa / UtilsSpecification), which cannot use btree indexes. Trigram GIN indexes on
 * the {@code lower(column)} expression make these searches index-backed.
 *
 * <p>The pg_trgm extension may not be installed or installable (managed databases, restricted
 * roles). The migration first tries {@code CREATE EXTENSION IF NOT EXISTS pg_trgm}; if the
 * extension is not available, it logs a warning and skips index creation gracefully so the platform
 * keeps working without it. The migration is idempotent: rerunning it after installing the
 * extension manually (then re-baselining) or on the next upgrade creates the indexes.
 */
@Component
@Slf4j
public class V5_23__Add_Trigram_Indexes_When_Available extends BaseJavaMigration {

  private static final String[][] TRIGRAM_INDEXES = {
    {"idx_trgm_injects_title", "injects", "inject_title"},
    {"idx_trgm_assets_name", "assets", "asset_name"},
    {"idx_trgm_scenarios_name", "scenarios", "scenario_name"},
    {"idx_trgm_exercises_name", "exercises", "exercise_name"},
    {"idx_trgm_teams_name", "teams", "team_name"},
    {"idx_trgm_users_email", "users", "user_email"},
    {"idx_trgm_documents_name", "documents", "document_name"},
    {"idx_trgm_payloads_name", "payloads", "payload_name"},
  };

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      if (!isTrigramExtensionAvailable(statement)) {
        log.warn(
            "pg_trgm extension is not installed and could not be created; "
                + "skipping trigram index creation. Install the extension manually "
                + "(CREATE EXTENSION pg_trgm) to enable index-backed text search.");
        return;
      }
      for (String[] index : TRIGRAM_INDEXES) {
        statement.execute(
            "CREATE INDEX IF NOT EXISTS "
                + index[0]
                + " ON "
                + index[1]
                + " USING gin (lower("
                + index[2]
                + ") gin_trgm_ops)");
      }
    }
  }

  /**
   * Checks for pg_trgm and tries to create it when missing. The creation attempt is wrapped in a
   * savepoint: a failed statement marks the surrounding Flyway transaction as aborted, so rolling
   * back to the savepoint is required for the migration to keep running (and commit) after a
   * permission failure on managed databases.
   */
  private boolean isTrigramExtensionAvailable(Statement statement) {
    try {
      try (ResultSet rs =
          statement.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'")) {
        if (rs.next()) {
          return true;
        }
      }
      statement.execute("SAVEPOINT before_trgm_extension");
      try {
        statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        statement.execute("RELEASE SAVEPOINT before_trgm_extension");
        return true;
      } catch (SQLException e) {
        statement.execute("ROLLBACK TO SAVEPOINT before_trgm_extension");
        log.warn("Unable to create pg_trgm extension: {}", e.getMessage());
        return false;
      }
    } catch (SQLException e) {
      log.warn("Unable to check pg_trgm extension availability: {}", e.getMessage());
      return false;
    }
  }
}
