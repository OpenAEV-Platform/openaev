package io.openaev.migration;

import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.service.account.ReservedServiceAccount;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Records the OpenCTI connector's service account as scheduler of the scenarios OpenCTI scheduled
 * before the scheduler was recorded. A scheduled simulation runs with its scheduler's clearance:
 * with none, it skips every marked target.
 *
 * <p>The account of a tenant is found by its email, derived from the tenant id like the connector
 * builds it. A tenant whose account does not exist yet keeps its scenarios unchanged. A scenario
 * scheduled by hand keeps its scheduler.
 */
@Component
public class V6_20261127110000000__Record_opencti_scenarios_scheduler extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (PreparedStatement update =
        connection.prepareStatement(
            """
            UPDATE scenarios s SET scenario_scheduled_by = u.user_id
            FROM users u
            WHERE lower(u.user_email) = ?
              AND s.tenant_id = ?
              AND s.scenario_scheduled_by IS NULL
              AND s.scenario_recurrence IS NOT NULL
              AND EXISTS (SELECT 1 FROM security_coverages sc
                          WHERE sc.security_coverage_scenario = s.scenario_id)
            """)) {
      for (String tenantId : tenantIds(connection)) {
        update.setString(1, connectorAccountEmail(tenantId));
        update.setString(2, tenantId);
        update.addBatch();
      }
      update.executeBatch();
    }
  }

  private static List<String> tenantIds(Connection connection) throws Exception {
    List<String> tenantIds = new ArrayList<>();
    try (Statement select = connection.createStatement();
        ResultSet rows = select.executeQuery("SELECT tenant_id FROM tenants")) {
      while (rows.next()) {
        tenantIds.add(rows.getString(1));
      }
    }
    return tenantIds;
  }

  private static String connectorAccountEmail(String tenantId) {
    return ReservedServiceAccount.OPENCTI_CONNECTOR
        .emailFor(SecurityCoverageConnector.idOf(tenantId))
        .toLowerCase();
  }
}
