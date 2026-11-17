package io.openaev.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Marks as already notified every tenant that is lost on XTM Hub when this version is installed, so
 * that only the connectivity losses that start afterwards send the tenant email.
 *
 * <p>A tenant that reaches XTM Hub again is armed again by the platform, so the reset costs it
 * nothing.
 */
@Component
public class V6_20261116180000000__Reset_xtmhub_connectivity_email_markers
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    resetMarkersOfLostTenants(context.getConnection());
  }

  /** Returns the number of registrations whose marker changed. */
  static int resetMarkersOfLostTenants(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      return statement.executeUpdate(
          "UPDATE tenant_xtmhub_registrations"
              + " SET registration_connectivity_email_eligible = false"
              + " WHERE registration_status = 'LOST_CONNECTIVITY'"
              + " AND registration_connectivity_email_eligible");
    }
  }
}
