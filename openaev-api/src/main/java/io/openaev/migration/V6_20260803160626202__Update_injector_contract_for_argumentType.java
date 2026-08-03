package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.PrimitiveType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260803160626202__Update_injector_contract_for_argumentType
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    try (Statement select = context.getConnection().createStatement();
        ResultSet results =
            select.executeQuery(
                "SELECT ic.injector_contract_id, ic.injector_contract_content, p.payload_arguments "
                    + "FROM injectors_contracts ic "
                    + "JOIN payloads p ON ic.injector_contract_payload = p.payload_id "
                    + "WHERE ic.injector_contract_content IS NOT NULL "
                    + "AND p.payload_arguments IS NOT NULL");
        PreparedStatement update =
            context
                .getConnection()
                .prepareStatement(
                    "UPDATE injectors_contracts SET injector_contract_content = ? "
                        + "WHERE injector_contract_id = ?")) {

      while (results.next()) {
        String contractId = results.getString("injector_contract_id");
        String contractContentRaw = results.getString("injector_contract_content");
        String payloadArgumentsRaw = results.getString("payload_arguments");

        JsonNode contractRoot = mapper.readTree(contractContentRaw);
        JsonNode payloadArguments = mapper.readTree(payloadArgumentsRaw);
        if (!(contractRoot instanceof ObjectNode contractContent)
            || !(payloadArguments instanceof ArrayNode arguments)
            || !contractContent.has("fields")
            || !contractContent.get("fields").isArray()) {
          continue;
        }

        Map<String, String> argumentTypesByKey = new HashMap<>();
        for (JsonNode argument : arguments) {
          String key = argument.path("key").asText(null);
          String type = argument.path("type").asText(null);
          if (key == null || type == null || PrimitiveType.fromLabelOptional(type).isEmpty()) {
            continue;
          }
          argumentTypesByKey.put(key, type);
        }
        if (argumentTypesByKey.isEmpty()) {
          continue;
        }

        ArrayNode fields = (ArrayNode) contractContent.get("fields");
        boolean modified = false;
        for (JsonNode field : fields) {
          if (!(field instanceof ObjectNode fieldNode)) {
            continue;
          }
          String key = fieldNode.path("key").asText(null);
          if (key == null || !argumentTypesByKey.containsKey(key)) {
            continue;
          }
          String argumentType = argumentTypesByKey.get(key);
          if (!argumentType.equals(fieldNode.path("argumentType").asText(null))) {
            fieldNode.put("argumentType", argumentType);
            modified = true;
          }
        }

        if (modified) {
          update.setString(1, mapper.writeValueAsString(contractContent));
          update.setString(2, contractId);
          update.addBatch();
        }
      }
      update.executeBatch();
    }
  }
}
