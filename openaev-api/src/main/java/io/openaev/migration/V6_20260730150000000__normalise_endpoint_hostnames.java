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
 * <p>Re-dated copy (2026-07-30) of {@link V6_20260729093849995__normalise_endpoint_hostnames}: the
 * original timestamp sorted BEFORE migrations already applied on rolling (main-build) databases
 * (they had run the 6.20260729120000000+ block before the original merged), so Flyway validation
 * failed with "resolved migration not applied" and out-of-order disabled. The original cannot be
 * deleted because it shipped in release 3.260729.0; instead it is skipped where out of order
 * (default {@code ignore-migration-patterns=*:missing,*:ignored}) and this copy runs the same
 * normalisation at the end of the block. Sanitising an already-sanitised hostname is a no-op, so
 * databases that already ran the original re-run this one safely.
 */
@Component
public class V6_20260730150000000__normalise_endpoint_hostnames extends BaseJavaMigration {

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
