package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.PayloadCommandBlock;
import io.openaev.database.model.StatusPayload;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_53__Update_Commands_In_Inject_Status extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();

    select.execute("ALTER TABLE injects_statuses ADD status_payload_output json;");

    select.execute(
        "UPDATE injects_statuses SET status_commands_lines=null WHERE status_commands_lines='null';");
    ResultSet results =
        select.executeQuery("SELECT status_id,status_commands_lines FROM injects_statuses");

    PreparedStatement statement =
        context
            .getConnection()
            .prepareStatement(
                "UPDATE injects_statuses SET status_payload_output = ?::json WHERE status_id=?");

    ObjectMapper mapper = new ObjectMapper();
    while (results.next()) {
      String commandLine = results.getString("status_commands_lines");
      String statusId = results.getString("status_id");
      if (commandLine != null) {
        JsonNode jsonNode = mapper.readTree(commandLine);
        JsonNode content = jsonNode.get("content");
        String contentString = "";
        if (content.isArray()) {
          for (JsonNode node : content) {
            contentString += node.asText();
          }
        }
        JsonNode cleanupCommand = jsonNode.get("cleanup_command");
        List<String> cleanupCommandList = new ArrayList<>();
        if (cleanupCommand.isArray()) {
          for (JsonNode node : cleanupCommand) {
            cleanupCommandList.add(node.asText());
          }
        }
        String externalId = jsonNode.get("external_id").asText();
        StatusPayload statusPayload =
            new StatusPayload(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                externalId,
                null,
                null,
                List.of(new PayloadCommandBlock(null, contentString, cleanupCommandList)),
                null);
        String value = mapper.writeValueAsString(statusPayload);
        statement.setString(1, value);
        statement.setString(2, statusId);
        statement.addBatch();
      }
    }
    statement.executeBatch();
    select.execute("ALTER TABLE injects_statuses DROP column status_commands_lines;");
  }
}
