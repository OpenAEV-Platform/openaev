package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_65__Update_Inject_Status_status_payload extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement statement = connection.createStatement();

    String addParamsToStatusPayload =
        "UPDATE injects_statuses "
            + "SET status_payload_output = jsonb_set("
            + "    jsonb_set("
            + "        jsonb_set(status_payload_output::jsonb, '{payload_name}', 'null'::jsonb), "
            + "        '{payload_description}', 'null'::jsonb), "
            + "    '{payload_type}', 'null'::jsonb) "
            + "WHERE status_payload_output IS NOT NULL;";

    String updatePayloadCommandBlocks =
        "UPDATE injects_statuses "
            + "SET status_payload_output = jsonb_set("
            + "    status_payload_output::jsonb,"
            + "    '{payload_command_blocks}',"
            + "    '[]'::jsonb)"
            + "WHERE status_payload_output->'payload_command_blocks' IS NULL;";

    statement.executeUpdate(addParamsToStatusPayload);
    statement.executeUpdate(updatePayloadCommandBlocks);
  }
}
