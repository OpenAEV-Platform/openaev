package io.openaev.migration;

import io.openaev.utils.catalog.SemanticVersion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Lets the Available tabs search the catalogue in the database: latest offered versions, types. */
@Component
public class V6_20261122235000000__Catalog_items_search extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS catalog_item_latest_offered"
              + " BOOLEAN NOT NULL DEFAULT false;");
      statement.execute(
          """
          UPDATE catalog_items SET catalog_item_scenario_type =
            CASE WHEN lower(catalog_item_scenario_type) = 'chained' THEN 'CHAINED' ELSE 'TIME_BASED' END
          WHERE catalog_item_type = 'SCENARIO';
          """);
    }
    markLatestOffered(connection);
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_catalog_items_latest_offered"
              + " ON catalog_items (tenant_id, catalog_item_type) WHERE catalog_item_latest_offered;");
    }
  }

  // The highest verified version of each item of each tenant.
  private static void markLatestOffered(Connection connection) throws Exception {
    Map<String, String[]> latest = new HashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "SELECT catalog_item_id, tenant_id, catalog_item_type, catalog_item_external_id,"
                    + " catalog_item_version FROM catalog_items"
                    + " WHERE catalog_item_status = 'VERIFIED'")) {
      while (rows.next()) {
        String key = rows.getString(2) + "/" + rows.getString(3) + "/" + rows.getString(4);
        String[] row = {rows.getString(1), rows.getString(5)};
        latest.merge(key, row, (a, b) -> SemanticVersion.ORDER.compare(a[1], b[1]) >= 0 ? a : b);
      }
    }
    try (PreparedStatement update =
        connection.prepareStatement(
            "UPDATE catalog_items SET catalog_item_latest_offered = true WHERE catalog_item_id = ?")) {
      for (String[] row : latest.values()) {
        update.setString(1, row[0]);
        update.addBatch();
      }
      update.executeBatch();
    }
  }
}
