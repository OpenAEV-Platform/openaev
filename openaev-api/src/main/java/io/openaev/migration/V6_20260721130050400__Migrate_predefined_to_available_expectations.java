package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260721130050400__Migrate_predefined_to_available_expectations
    extends BaseJavaMigration {

  private static final String PREDEFINED_EXPECTATIONS = "predefinedExpectations";
  private static final String AVAILABLE_EXPECTATIONS = "availableExpectations";
  private static final String IS_PREDEFINED = "expectation_is_predefined";

  private static final String[][] REQUIRED_EXPECTATIONS = {
    {"DETECTION", "Detection"},
    {"PREVENTION", "Prevention"},
    {"VULNERABILITY", "Not vulnerable"}
  };

  private static final int BATCH_SIZE = 500;

  @Override
  public void migrate(Context context) throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    try (Statement select = context.getConnection().createStatement();
        ResultSet results =
            select.executeQuery(
                "SELECT injector_contract_content, injector_contract_id, "
                    + "injector_contract_payload IS NOT NULL AS has_payload "
                    + "FROM injectors_contracts "
                    + "WHERE injector_contract_content IS NOT NULL");
        PreparedStatement update =
            context
                .getConnection()
                .prepareStatement(
                    "UPDATE injectors_contracts SET injector_contract_content = ? "
                        + "WHERE injector_contract_id = ?")) {

      int batchCount = 0;

      while (results.next()) {
        String content = results.getString("injector_contract_content");
        String contractId = results.getString("injector_contract_id");
        boolean hasPayload = results.getBoolean("has_payload");

        ObjectNode contractContent = (ObjectNode) mapper.readTree(content);
        if (contractContent == null || !contractContent.has("fields")) {
          continue;
        }

        ObjectNode expectationsField =
            (ObjectNode)
                StreamSupport.stream(contractContent.get("fields").spliterator(), false)
                    .filter(f -> "expectations".equals(f.path("key").asText()))
                    .findFirst()
                    .orElse(null);

        if (expectationsField == null) {
          continue;
        }

        boolean modified = false;

        // Get or create availableExpectations array
        ArrayNode available;
        if (expectationsField.has(AVAILABLE_EXPECTATIONS)
            && expectationsField.get(AVAILABLE_EXPECTATIONS).isArray()) {
          available = (ArrayNode) expectationsField.get(AVAILABLE_EXPECTATIONS);
          // Ensure all existing entries have the is_predefined field (default to false)
          for (JsonNode entry : available) {
            if (!entry.has(IS_PREDEFINED)) {
              ((ObjectNode) entry).put(IS_PREDEFINED, false);
              modified = true;
            }
          }
        } else {
          available = mapper.createArrayNode();
        }

        // Merge predefinedExpectations into availableExpectations
        if (expectationsField.has(PREDEFINED_EXPECTATIONS)) {
          ArrayNode predefined = (ArrayNode) expectationsField.get(PREDEFINED_EXPECTATIONS);
          for (JsonNode exp : predefined) {
            String expType = exp.path("expectation_type").asText();
            Optional<JsonNode> existingOpt =
                StreamSupport.stream(available.spliterator(), false)
                    .filter(e -> e.path("expectation_type").asText().equals(expType))
                    .findFirst();

            if (existingOpt.isEmpty()) {
              ObjectNode expCopy = exp.deepCopy();
              expCopy.put(IS_PREDEFINED, true);
              available.add(expCopy);
            } else {
              ((ObjectNode) existingOpt.get()).put(IS_PREDEFINED, true);
            }
          }
          modified = true;
        }

        // For contracts linked to a payload, ensure DETECTION, PREVENTION, VULNERABILITY exist
        if (hasPayload) {
          for (String[] required : REQUIRED_EXPECTATIONS) {
            String type = required[0];
            String name = required[1];
            boolean exists =
                StreamSupport.stream(available.spliterator(), false)
                    .anyMatch(e -> type.equals(e.path("expectation_type").asText()));
            if (!exists) {
              ObjectNode newExp = mapper.createObjectNode();
              newExp.put("expectation_type", type);
              newExp.put("expectation_name", name);
              newExp.putNull("expectation_description");
              newExp.put("expectation_score", 100.0);
              newExp.put("expectation_expectation_group", false);
              newExp.put("expectation_is_multi_selectable", false);
              newExp.put("expectation_expiration_time", 21600);
              newExp.put(IS_PREDEFINED, false);
              available.add(newExp);
              modified = true;
            }
          }
        }

        if (modified) {
          expectationsField.set(AVAILABLE_EXPECTATIONS, available);
          if (expectationsField.has(PREDEFINED_EXPECTATIONS)) {
            expectationsField.remove(PREDEFINED_EXPECTATIONS);
          }
          String updatedContent = mapper.writeValueAsString(contractContent);
          update.setString(1, updatedContent);
          update.setString(2, contractId);
          update.addBatch();
          batchCount++;
          if (batchCount == BATCH_SIZE) {
            update.executeBatch();
            batchCount = 0;
          }
        }
      }

      if (batchCount != 0) {
        update.executeBatch();
      }
    }
  }
}
