package io.openaev.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * Gives the injector contract fields of a payload argument of type document their own field type
 * (#6973). Such fields were stored as {@code "type": "text"} with {@code "argumentType":
 * "document"}; contracts generated from now on carry {@code "type": "document"}, so stored
 * contracts are aligned here and render the document picker without the payload being saved again.
 */
@Component
public class V6_20261112080000000__Retype_document_argument_contract_fields
    extends BaseJavaMigration {

  private static final String CONTRACT_FIELDS_NODE = "fields";
  private static final String FIELD_TYPE = "type";
  private static final String FIELD_ARGUMENT_TYPE = "argumentType";
  private static final String TEXT_TYPE = "text";
  private static final String DOCUMENT_TYPE = "document";
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
                "SELECT injector_contract_id, tenant_id, injector_contract_content "
                    + "FROM injectors_contracts "
                    + "WHERE injector_contract_payload IS NOT NULL")) {

      int pendingUpdates = 0;
      while (contracts.next()) {
        String updatedContent =
            withDocumentFieldType(mapper, contracts.getString("injector_contract_content"));
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

  /**
   * Retypes the text fields whose argument type is document.
   *
   * @return the updated content, or {@code null} when nothing had to change
   */
  static String withDocumentFieldType(ObjectMapper mapper, String rawContent)
      throws JsonProcessingException {
    if (rawContent == null
        || !(mapper.readTree(rawContent) instanceof ObjectNode content)
        || !(content.get(CONTRACT_FIELDS_NODE) instanceof ArrayNode fields)) {
      return null;
    }

    boolean changed = false;
    for (JsonNode field : fields) {
      if (field instanceof ObjectNode fieldNode
          && TEXT_TYPE.equals(fieldNode.path(FIELD_TYPE).asText())
          && DOCUMENT_TYPE.equals(fieldNode.path(FIELD_ARGUMENT_TYPE).asText())) {
        fieldNode.put(FIELD_TYPE, DOCUMENT_TYPE);
        changed = true;
      }
    }
    return changed ? mapper.writeValueAsString(content) : null;
  }
}
