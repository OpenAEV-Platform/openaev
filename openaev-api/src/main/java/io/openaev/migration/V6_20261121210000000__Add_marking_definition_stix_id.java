package io.openaev.migration;

import io.openaev.database.model.MarkingDefinitionStixIds;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Marking definitions carry the STIX {@code standard_id} OpenCTI gives them, so one marking is the
 * same object across the products of the platform. Additive: a nullable column, unique per tenant,
 * and a backfill of every existing definition with the id OpenCTI derives from its type and
 * definition (the static ids for the five TLP 2.0 markings). Local ids, {@code marking_ids} and the
 * clearance functions are untouched.
 *
 * <p>Two definitions of one tenant that OpenCTI names with the same id (TLP:WHITE next to
 * TLP:CLEAR) cannot share it: the oldest keeps it, the other stays without one. A rerun only fills
 * rows still without an id.
 */
@Component
public class V6_20261121210000000__Add_marking_definition_stix_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE marking_definitions"
              + " ADD COLUMN IF NOT EXISTS marking_definition_stix_id VARCHAR(255)");
    }
    backfill(connection);
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_marking_definitions_stix_id_tenant_uq"
              + " ON marking_definitions (marking_definition_stix_id, tenant_id)"
              + " WHERE marking_definition_stix_id IS NOT NULL");
    }
  }

  private static void backfill(Connection connection) throws Exception {
    Set<String> taken = new HashSet<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT tenant_id, marking_definition_stix_id FROM marking_definitions"
                    + " WHERE marking_definition_stix_id IS NOT NULL")) {
      while (rows.next()) {
        taken.add(rows.getString(1) + "|" + rows.getString(2));
      }
    }
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT marking_definition_id, tenant_id, marking_definition_type,"
                    + " marking_definition_definition FROM marking_definitions"
                    + " WHERE marking_definition_stix_id IS NULL"
                    + " ORDER BY marking_definition_created_at, marking_definition_id");
        PreparedStatement update =
            connection.prepareStatement(
                "UPDATE marking_definitions SET marking_definition_stix_id = ?"
                    + " WHERE marking_definition_id = ?")) {
      int pending = 0;
      while (rows.next()) {
        String stixId = MarkingDefinitionStixIds.generate(rows.getString(3), rows.getString(4));
        if (!taken.add(rows.getString(2) + "|" + stixId)) {
          continue;
        }
        update.setString(1, stixId);
        update.setString(2, rows.getString(1));
        update.addBatch();
        pending++;
      }
      if (pending > 0) {
        update.executeBatch();
      }
    }
  }
}
