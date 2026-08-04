package io.openaev.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * Backfills the {@code argumentType} property on injector contract fields from the arguments of the
 * linked payload. Arguments whose type is not a known {@link PrimitiveType} are left alone, as are
 * contracts without payload and fields without a matching argument key.
 */
@Component
public class V6_20260803193407141__Add_argumentType_to_injectorcontract extends BaseJavaMigration {

  private static final String CONTRACT_FIELDS_NODE = "fields";
  private static final String FIELD_KEY = "key";
  private static final String FIELD_ARGUMENT_TYPE = "argumentType";
  private static final String ARGUMENT_KEY = "key";
  private static final String ARGUMENT_TYPE = "type";
  private static final int BATCH_SIZE = 500;

  @Override
  public void migrate(Context context) throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    try (PreparedStatement update =
            context
                .getConnection()
                .prepareStatement(
                    "UPDATE injectors_contracts SET injector_contract_content = ? "
                        + "WHERE injector_contract_id = ? AND tenant_id = ?");
        Statement select = context.getConnection().createStatement();
        ResultSet contracts =
            select.executeQuery(
                "SELECT ic.injector_contract_id, ic.tenant_id, ic.injector_contract_content, "
                    + "p.payload_arguments "
                    + "FROM injectors_contracts ic "
                    + "JOIN payloads p ON p.payload_id = ic.injector_contract_payload "
                    + "WHERE p.payload_arguments IS NOT NULL")) {

      int pendingUpdates = 0;
      while (contracts.next()) {
        Map<String, String> argumentTypesByKey =
            primitiveArgumentTypes(mapper, contracts.getString("payload_arguments"));
        if (argumentTypesByKey.isEmpty()) {
          continue;
        }
        String updatedContent =
            withArgumentTypes(
                mapper, contracts.getString("injector_contract_content"), argumentTypesByKey);
        if (updatedContent == null) {
          continue;
        }

        update.setString(1, updatedContent);
        update.setString(2, contracts.getString("injector_contract_id"));
        update.setString(3, contracts.getString("tenant_id"));
        update.addBatch();
        if (++pendingUpdates == BATCH_SIZE) {
          update.executeBatch();
          pendingUpdates = 0;
        }
      }
      if (pendingUpdates > 0) {
        update.executeBatch();
      }
    }
  }

  /** Maps payload argument keys to their type, keeping only known primitive types. */
  private static Map<String, String> primitiveArgumentTypes(
      ObjectMapper mapper, String rawPayloadArguments) throws JsonProcessingException {
    JsonNode arguments = mapper.readTree(rawPayloadArguments);
    if (!arguments.isArray()) {
      return Map.of();
    }

    Map<String, String> argumentTypesByKey = new HashMap<>();
    for (JsonNode argument : arguments) {
      JsonNode key = argument.get(ARGUMENT_KEY);
      JsonNode type = argument.get(ARGUMENT_TYPE);
      if (key == null
          || !key.isTextual()
          || type == null
          || !type.isTextual()
          || PrimitiveType.fromLabelOptional(type.asText()).isEmpty()) {
        continue;
      }
      argumentTypesByKey.put(key.asText(), type.asText());
    }
    return argumentTypesByKey;
  }

  /**
   * Applies the argument types to the matching contract fields.
   *
   * @return the updated content, or {@code null} when nothing had to change
   */
  private static String withArgumentTypes(
      ObjectMapper mapper, String rawContent, Map<String, String> argumentTypesByKey)
      throws JsonProcessingException {
    if (!(mapper.readTree(rawContent) instanceof ObjectNode content)
        || !(content.get(CONTRACT_FIELDS_NODE) instanceof ArrayNode fields)) {
      return null;
    }

    boolean changed = false;
    for (JsonNode field : fields) {
      if (!(field instanceof ObjectNode fieldNode)) {
        continue;
      }
      JsonNode key = fieldNode.get(FIELD_KEY);
      if (key == null || !key.isTextual()) {
        continue;
      }
      // Fields derived from an argument (targeted-property-*, targeted-asset-separator-*) carry no
      // matching argument key and are therefore left untouched.
      String argumentType = argumentTypesByKey.get(key.asText());
      if (argumentType == null) {
        continue;
      }
      JsonNode currentArgumentType = fieldNode.get(FIELD_ARGUMENT_TYPE);
      if (currentArgumentType != null
          && currentArgumentType.isTextual()
          && argumentType.equals(currentArgumentType.asText())) {
        continue;
      }
      fieldNode.put(FIELD_ARGUMENT_TYPE, argumentType);
      changed = true;
    }
    return changed ? mapper.writeValueAsString(content) : null;
  }
}
