package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_24__Payload_collector extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("ALTER TABLE payloads ADD COLUMN payload_collector varchar(255);");
    select.execute(
        "ALTER TABLE payloads ADD CONSTRAINT collector_fk FOREIGN KEY (payload_collector) REFERENCES collectors(collector_id) ON DELETE CASCADE;");
  }
}
