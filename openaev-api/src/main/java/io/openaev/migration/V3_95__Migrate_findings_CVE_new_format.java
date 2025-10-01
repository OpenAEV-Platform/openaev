package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_95__Migrate_findings_CVE_new_format extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    // ES
    statement.executeUpdate("DELETE FROM indexing_status WHERE indexing_status_type = 'finding';");

    // POSTGRES - FINDINGS

    // 1. Drop the unique constraint
    statement.executeUpdate(
        "ALTER TABLE findings " + "DROP CONSTRAINT IF EXISTS unique_finding_constraint;");

    // 2. Apply transformation to finding_value
    statement.executeUpdate(
        "UPDATE findings "
            + "SET finding_value = trim(substring(finding_value FROM '^[^:]+:([^()]+)'))"
            + "WHERE finding_type = 'CVE' AND finding_value ~ '^[^:]+:([^()]+)';");

    // 3. Compute duplicates and create a temp table for mapping
    statement.executeUpdate(
        "CREATE TEMP TABLE finding_dedup_map AS "
            + "SELECT d.finding_id AS old_id, k.keep_id "
            + "FROM ( "
            + " SELECT finding_id, finding_inject_id, finding_type, finding_value, finding_field, "
            + " ROW_NUMBER() OVER (PARTITION BY finding_inject_id, finding_type, finding_value, finding_field ORDER BY finding_id) AS rn "
            + " FROM findings "
            + " WHERE finding_type = 'CVE' "
            + ") d "
            + "JOIN ( "
            + " SELECT finding_inject_id, finding_type, finding_value, finding_field, "
            + " MIN(finding_id) AS keep_id "
            + " FROM findings "
            + " WHERE finding_type = 'CVE' "
            + " GROUP BY finding_inject_id, finding_type, finding_value, finding_field "
            + ") k "
            + "ON d.finding_inject_id = k.finding_inject_id "
            + " AND d.finding_type = k.finding_type "
            + " AND d.finding_value = k.finding_value "
            + " AND d.finding_field = k.finding_field "
            + "WHERE d.rn > 1;");

    // 4. Update all related tables using the dedup map
    String[] relatedTables = {
      "findings_assets", "findings_teams", "findings_tags", "findings_users"
    };

    for (String table : relatedTables) {
      statement.executeUpdate(
          "UPDATE "
              + table
              + " t "
              + "SET finding_id = map.keep_id "
              + "FROM finding_dedup_map map "
              + "WHERE t.finding_id = map.old_id;");
    }

    // 5. Delete duplicate findings
    statement.executeUpdate(
        "DELETE FROM findings " + "WHERE finding_id IN (SELECT old_id FROM finding_dedup_map);");

    // 6. Recreate the unique constraint
    statement.executeUpdate(
        "ALTER TABLE findings "
            + "ADD CONSTRAINT unique_finding_constraint "
            + "UNIQUE (finding_inject_id, finding_type, finding_value, finding_field);");
  }
}
