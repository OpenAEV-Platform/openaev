package io.openaev.migration;

import io.openaev.utils.sanitisation.DomainSanitiser;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

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
