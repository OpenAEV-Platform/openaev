package io.openaev.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20261105100000000__Add_finding_occurrences extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      createFindingLocations(statement);
      prepareFindings(statement);
      createFindingOccurrences(statement);
      linkPhishingResultsToOccurrences(statement);
    }
    backfillOccurrences(connection);
    linkPhishingResultsToTheirOccurrence(connection);
  }

  // One row per asset, one per user and a single unknown row. The key must stay equal to
  // FindingLocation.key()
  private void createFindingLocations(Statement statement) throws SQLException {
    statement.execute(
        """
        CREATE TABLE IF NOT EXISTS finding_locations (
            finding_location_id VARCHAR(255) NOT NULL CONSTRAINT finding_locations_pkey PRIMARY KEY,
            finding_location_asset_id VARCHAR(255)
                CONSTRAINT finding_locations_asset_fk REFERENCES assets (asset_id)
                ON DELETE CASCADE
                CONSTRAINT finding_locations_asset_unique UNIQUE,
            finding_location_user_id VARCHAR(255)
                CONSTRAINT finding_locations_user_fk REFERENCES users (user_id)
                ON DELETE CASCADE
                CONSTRAINT finding_locations_user_unique UNIQUE,
            finding_location_key TEXT GENERATED ALWAYS AS (COALESCE(
                'asset:' || finding_location_asset_id,
                'user:' || finding_location_user_id,
                'unknown')) STORED
                CONSTRAINT finding_locations_key_unique UNIQUE,
            CONSTRAINT finding_locations_one_location_check
                CHECK (num_nonnulls(finding_location_asset_id, finding_location_user_id) <= 1)
        )
        """);
  }

  private void prepareFindings(Statement statement) throws SQLException {
    // A stable finding belongs to no single inject, and its field moves to the occurrence
    statement.execute("ALTER TABLE findings ALTER COLUMN finding_inject_id DROP NOT NULL");
    statement.execute("ALTER TABLE findings ALTER COLUMN finding_field DROP NOT NULL");
    statement.execute(
        "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_value_hash VARCHAR(64)");
    statement.execute(
        "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_location_id VARCHAR(255)"
            + " CONSTRAINT findings_location_fk REFERENCES finding_locations (finding_location_id)"
            + " ON DELETE CASCADE");
    statement.execute(
        "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_first_seen TIMESTAMPTZ");
    statement.execute(
        "ALTER TABLE findings ADD COLUMN IF NOT EXISTS finding_last_seen TIMESTAMPTZ");
    statement.execute(
        "CREATE INDEX IF NOT EXISTS idx_findings_location ON findings (finding_location_id)");
    // Existing rows have a NULL hash, and NULLs never conflict: the key can exist before the writer
    statement.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS findings_identity_unique ON findings"
            + " (tenant_id, finding_type, finding_value_hash, finding_location_id)");
  }

  // A relation table: its tenant is its finding's
  private void createFindingOccurrences(Statement statement) throws SQLException {
    statement.execute(
        """
        CREATE TABLE IF NOT EXISTS finding_occurrences (
            finding_occurrence_id VARCHAR(255) NOT NULL
                CONSTRAINT finding_occurrences_pkey PRIMARY KEY,
            finding_occurrence_finding_id VARCHAR(255) NOT NULL
                CONSTRAINT finding_occurrences_finding_fk REFERENCES findings (finding_id)
                ON DELETE CASCADE,
            finding_occurrence_inject_id VARCHAR(255)
                CONSTRAINT finding_occurrences_inject_fk REFERENCES injects (inject_id)
                ON DELETE SET NULL,
            finding_occurrence_date TIMESTAMPTZ NOT NULL,
            finding_occurrence_field VARCHAR(255),
            finding_occurrence_created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            CONSTRAINT finding_occurrences_finding_inject_unique
                UNIQUE (finding_occurrence_finding_id, finding_occurrence_inject_id)
        )
        """);
    // The finding FK is served by the unique key, which starts with it
    statement.execute(
        "CREATE INDEX IF NOT EXISTS idx_finding_occurrences_inject"
            + " ON finding_occurrences (finding_occurrence_inject_id)");
  }

  private void linkPhishingResultsToOccurrences(Statement statement) throws SQLException {
    statement.execute(
        "ALTER TABLE phishing_results ADD COLUMN IF NOT EXISTS phishing_result_finding_occurrence"
            + " VARCHAR(255) CONSTRAINT phishing_results_finding_occurrence_fk"
            + " REFERENCES finding_occurrences (finding_occurrence_id) ON DELETE SET NULL");
    statement.execute(
        "CREATE INDEX IF NOT EXISTS idx_phishing_results_finding_occurrence"
            + " ON phishing_results (phishing_result_finding_occurrence)");
  }

  // Each batch takes the next findings by id, inserts their occurrence (inject, the inject's sent
  // date or else the finding's creation date, field) and returns its last id, NULL once no finding
  // is left
  private void backfillOccurrences(Connection connection) throws SQLException {
    String sql =
        "WITH batch AS (SELECT f.finding_id, f.finding_inject_id, f.finding_field,"
            + " f.finding_created_at FROM findings f"
            + " WHERE f.finding_inject_id IS NOT NULL AND f.finding_id > ?"
            + " ORDER BY f.finding_id LIMIT "
            + BATCH_SIZE
            + "), inserted AS (INSERT INTO finding_occurrences (finding_occurrence_id,"
            + " finding_occurrence_finding_id, finding_occurrence_inject_id,"
            + " finding_occurrence_date, finding_occurrence_field, finding_occurrence_created_at)"
            + " SELECT gen_random_uuid(), b.finding_id, b.finding_inject_id,"
            + " COALESCE(s.tracking_sent_date, b.finding_created_at, now()), b.finding_field, now()"
            + " FROM batch b"
            + " LEFT JOIN injects_statuses s ON s.status_inject = b.finding_inject_id"
            + " ON CONFLICT (finding_occurrence_finding_id, finding_occurrence_inject_id)"
            + " DO NOTHING)"
            + " SELECT max(finding_id) FROM batch";
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      String lastId = "";
      while (lastId != null) {
        statement.setString(1, lastId);
        try (ResultSet resultSet = statement.executeQuery()) {
          resultSet.next();
          lastId = resultSet.getString(1);
        }
      }
    }
  }

  private void linkPhishingResultsToTheirOccurrence(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "UPDATE phishing_results r"
              + " SET phishing_result_finding_occurrence = o.finding_occurrence_id"
              + " FROM finding_occurrences o"
              + " WHERE o.finding_occurrence_finding_id = r.phishing_result_finding"
              + " AND r.phishing_result_finding_occurrence IS NULL");
    }
  }
}
