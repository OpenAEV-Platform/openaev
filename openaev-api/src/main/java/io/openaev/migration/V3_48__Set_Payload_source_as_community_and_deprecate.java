package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_48__Set_Payload_source_as_community_and_deprecate extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();
    statement.execute(
        "UPDATE payloads SET payload_source = 'COMMUNITY' WHERE payload_collector IS NOT NULL AND payload_source = 'MANUAL';");
    statement.execute(
        "UPDATE payloads SET payload_status = 'DEPRECATED' FROM collectors\n"
            + "    WHERE payload_collector = collector_id AND collector_last_execution - payload_updated_at >= INTERVAL '1 week';");
  }
}
