package io.openaev.migration;

import io.openaev.utils.sanitisation.DomainSanitiser;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Normalises existing endpoint hostnames through {@link DomainSanitiser} (issue 0229).
 *
 * <p>KEPT FOR RELEASE COMPATIBILITY - this migration shipped in release 3.260729.0, so it must stay
 * in the resolved set (databases on that release have it applied, and the Migrations Guard enforces
 * that the released block is never altered). However, it was merged AFTER the 6.20260729120000000+
 * block was already applied on rolling (main-build) databases, so on those databases it is
 * permanently out of order: the default {@code ignore-migration-patterns=*:missing,*:ignored} lets
 * validation pass and Flyway skips it. The re-dated copy {@link
 * V6_20260730150000000__normalise_endpoint_hostnames} performs the same normalisation at the end of
 * the block on every database (re-running is a no-op).
 */
@Component
public class V6_20260729093849995__normalise_endpoint_hostnames extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    DomainSanitiser ds = new DomainSanitiser();
    Map<String, String> newHostnames = new HashMap<>();
    try (Statement statement = context.getConnection().createStatement()) {
      ResultSet rs = statement.executeQuery("SELECT asset_id, asset_hostname FROM assets");
      while (rs.next()) {
        newHostnames.put(rs.getString(1), ds.sanitise(rs.getString(2)));
      }
    }
    try (Statement statement = context.getConnection().createStatement()) {
      for (Map.Entry<String, String> entry : newHostnames.entrySet()) {
        statement.execute(
            "UPDATE assets SET asset_hostname = '"
                + entry.getValue()
                + "' WHERE asset_id = '"
                + entry.getKey()
                + "';");
      }
    }
  }
}
