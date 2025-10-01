package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_03__Update_injector_contracts_predefined_expectations extends BaseJavaMigration {

  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();

    select.executeUpdate(
        "INSERT INTO collectors (collector_id,collector_name,collector_type,collector_period,collector_external) "
            + "VALUES ('acab8214-0379-448a-a575-05e9d934eadd','Expectations Vulnerability Manager','openaev_expectations_vulnerability_manager',0,false);");

    ResultSet results =
        select.executeQuery(
            "SELECT injector_contract_content,injector_contract_id FROM injectors_contracts WHERE injector_contract_payload is not null");

    PreparedStatement statement =
        context
            .getConnection()
            .prepareStatement(
                "UPDATE injectors_contracts SET injector_contract_content = ? WHERE injector_contract_id=?");

    ObjectMapper mapper = new ObjectMapper();

    while (results.next()) {
      String content = results.getString("injector_contract_content");
      ObjectNode contractContent = mapper.readValue(content, ObjectNode.class);
      String contractId = results.getString("injector_contract_id");
      int fieldIndex = 0;
      if (contractContent != null) {
        ArrayNode fields = (ArrayNode) contractContent.get("fields");
        for (JsonNode field : fields) {
          String key = field.get("key").asText();
          if (key.equals("expectations")) {
            ArrayNode predefinedExpectations = (ArrayNode) field.get("predefinedExpectations");
            ObjectNode node = mapper.createObjectNode();
            node.put("expectation_type", "VULNERABILITY");
            node.put("expectation_name", "Expect asset to not be vulnerable");
            node.put("expectation_description", "");
            node.put("expectation_score", 100.0);
            node.put("expectation_expectation_group", false);
            node.put("expectation_expiration_time", 21600);
            predefinedExpectations.add(node);
            ObjectNode expectations = (ObjectNode) field;
            expectations.set("predefinedExpectations", predefinedExpectations);
            fields.set(fieldIndex, expectations);
            break;
          }
          fieldIndex++;
        }
        contractContent.set("fields", fields);
        String contentToSave = mapper.writeValueAsString(contractContent);
        statement.setString(1, contentToSave);
        statement.setString(2, contractId);
        statement.addBatch();
      }
    }
    statement.executeBatch();
  }
}
