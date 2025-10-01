package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_43__Payloads_default_values extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();

    select.executeUpdate(
        "UPDATE payloads SET payload_source = 'MANUAL' WHERE payload_source IS NULL;");
    select.executeUpdate(
        "UPDATE payloads SET payload_status = 'UNVERIFIED' WHERE payload_status IS NULL;");

    select.execute("ALTER TABLE payloads ALTER COLUMN payload_source SET DEFAULT 'MANUAL';");
    select.execute("ALTER TABLE payloads ALTER COLUMN payload_status SET DEFAULT 'UNVERIFIED';");
  }
}
