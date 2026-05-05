package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_93__Add_available_expectations_to_injector_contracts extends BaseJavaMigration {

  private static final String BACKUP_TABLE = "migration_v4_93_injector_contracts_backup";

  // Default expiration times (seconds) — mirrors ExpectationPropertiesConfig constants
  private static final long TECHNICAL_EXPIRATION_TIME = 21600L;
  private static final long HUMAN_EXPIRATION_TIME = 86400L;
  private static final double DEFAULT_SCORE = 100.0;

  // JSON field names
  private static final String FIELDS = "fields";
  private static final String KEY = "key";
  private static final String EXPECTATIONS_KEY = "expectations";
  private static final String AVAILABLE_EXPECTATIONS = "availableExpectations";

  // Injector type identifiers
  private static final String TYPE_EMAIL = "openaev_email";
  private static final String TYPE_CHANNEL = "openaev_channel";
  private static final String TYPE_CHALLENGE = "openaev_challenge";
  private static final String TYPE_MANUAL = "openaev_manual";

  // Expectation type/name constants
  private static final String EXPECTATION_MANUAL = "MANUAL";
  private static final String EXPECTATION_MANUAL_NAME = "Manual expectation";
  private static final String EXPECTATION_TEXT = "TEXT";
  private static final String EXPECTATION_TEXT_NAME = "Simple expectation";
  private static final String EXPECTATION_ARTICLE = "ARTICLE";
  private static final String EXPECTATION_ARTICLE_NAME = "Expect targets to read the article(s)";
  private static final String EXPECTATION_CHALLENGE = "CHALLENGE";
  private static final String EXPECTATION_CHALLENGE_NAME = "Expect targets to complete the challenge(s)";
  private static final String EXPECTATION_DETECTION = "DETECTION";
  private static final String EXPECTATION_DETECTION_NAME = "Detection";
  private static final String EXPECTATION_PREVENTION = "PREVENTION";
  private static final String EXPECTATION_PREVENTION_NAME = "Prevention";
  private static final String EXPECTATION_VULNERABILITY = "VULNERABILITY";
  private static final String EXPECTATION_VULNERABILITY_NAME = "Vulnerability";

  @Override
  public void migrate(Context context) throws Exception {
    ObjectMapper mapper = new ObjectMapper();

    createBackupTable(context);

    // Join with injectors to resolve the injector type for each contract.
    // DISTINCT ON ensures one row per contract even if linked to multiple injectors.
    // All injectors of a contract share the same type, so the first one is sufficient.
    String selectQuery =
        "SELECT DISTINCT ON (ic.injector_contract_id, ic.tenant_id) "
            + "ic.injector_contract_id, ic.tenant_id, ic.injector_contract_content, "
            + "ic.injector_contract_manual, i.injector_type "
            + "FROM injectors_contracts ic "
            + "LEFT JOIN injectors_injector_contracts iic "
            + "  ON iic.injector_contract_id = ic.injector_contract_id "
            + "  AND iic.tenant_id = ic.tenant_id "
            + "LEFT JOIN injectors i ON i.injector_id = iic.injector_id "
            + "WHERE ic.injector_contract_content IS NOT NULL";

    try (PreparedStatement select = context.getConnection().prepareStatement(selectQuery);
        ResultSet contracts = select.executeQuery();
        PreparedStatement backup =
            context
                .getConnection()
                .prepareStatement(
                    "INSERT INTO "
                        + BACKUP_TABLE
                        + " (injector_contract_id, tenant_id, injector_contract_content) "
                        + "VALUES (?, ?, ?) "
                        + "ON CONFLICT (injector_contract_id, tenant_id) DO NOTHING");
        PreparedStatement update =
            context
                .getConnection()
                .prepareStatement(
                    "UPDATE injectors_contracts "
                        + "SET injector_contract_content = ? "
                        + "WHERE injector_contract_id = ? AND tenant_id = ?")) {

      while (contracts.next()) {
        processContract(mapper, contracts, backup, update);
      }

      backup.executeBatch();
      update.executeBatch();
    }
  }

  private void createBackupTable(Context context) throws Exception {
    try (PreparedStatement createBackup =
        context
            .getConnection()
            .prepareStatement(
                "CREATE TABLE IF NOT EXISTS "
                    + BACKUP_TABLE
                    + " ("
                    + "injector_contract_id VARCHAR(255) NOT NULL, "
                    + "tenant_id VARCHAR(255) NOT NULL, "
                    + "injector_contract_content TEXT NOT NULL, "
                    + "PRIMARY KEY (injector_contract_id, tenant_id)"
                    + ")")) {
      createBackup.execute();
    }
  }

  private void processContract(
      ObjectMapper mapper,
      ResultSet contracts,
      PreparedStatement backup,
      PreparedStatement update)
      throws Exception {
    String contractId = contracts.getString("injector_contract_id");
    String tenantId = contracts.getString("tenant_id");
    String content = contracts.getString("injector_contract_content");
    boolean isManual = contracts.getBoolean("injector_contract_manual");
    String injectorType = contracts.getString("injector_type");

    ObjectNode contractContent = (ObjectNode) mapper.readTree(content);
    if (contractContent == null
        || !contractContent.has(FIELDS)
        || !contractContent.get(FIELDS).isArray()) {
      return;
    }

    ArrayNode fields = (ArrayNode) contractContent.get(FIELDS);
    boolean modified = normalizeAvailableExpectations(fields, isManual, injectorType);

    if (modified) {
      backup.setString(1, contractId);
      backup.setString(2, tenantId);
      backup.setString(3, content);
      backup.addBatch();

      update.setString(1, mapper.writeValueAsString(contractContent));
      update.setString(2, contractId);
      update.setString(3, tenantId);
      update.addBatch();
    }
  }

  /**
   * For each field with key {@code "expectations"}, unconditionally rebuilds
   * {@code availableExpectations} from the canonical set derived from the injector type.
   * This guarantees correct {@code expectation_is_limited} values regardless of whatever
   * pre-existing content may have been stored by a previous migration or injector registration.
   */
  private boolean normalizeAvailableExpectations(
      ArrayNode fields, boolean isManual, String injectorType) {
    boolean modified = false;
    for (JsonNode fieldNode : fields) {
      if (!(fieldNode instanceof ObjectNode field)) {
        continue;
      }
      if (!EXPECTATIONS_KEY.equals(field.path(KEY).asText())) {
        continue;
      }
      ArrayNode expected = buildAvailableExpectations(isManual, injectorType);
      JsonNode current = field.get(AVAILABLE_EXPECTATIONS);
      if (!expected.equals(current)) {
        field.set(AVAILABLE_EXPECTATIONS, expected);
        modified = true;
      }
    }
    return modified;
  }


  /**
   * Determines the correct set of available expectations based on injector contract type.
   *
   * <ul>
   *   <li>Manual (flag or type) → [MANUAL (not limited)]
   *   <li>Email → [TEXT (limited), MANUAL (not limited)]
   *   <li>Channel → [ARTICLE (limited), MANUAL (not limited)]
   *   <li>Challenge → [CHALLENGE (limited), MANUAL (not limited)]
   *   <li>Technical (payload or other external injectors) → [DETECTION (limited), PREVENTION (limited), VULNERABILITY (limited)]
   * </ul>
   */
  private ArrayNode buildAvailableExpectations(boolean isManual, String injectorType) {
    if (isManual || TYPE_MANUAL.equals(injectorType)) {
      return arrayOf(
          expectation(
              EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, false));
    }
    if (TYPE_EMAIL.equals(injectorType)) {
      return arrayOf(
          expectation(EXPECTATION_TEXT, EXPECTATION_TEXT_NAME, HUMAN_EXPIRATION_TIME, true),
          expectation(
              EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, false));
    }
    if (TYPE_CHANNEL.equals(injectorType)) {
      return arrayOf(
          expectation(
              EXPECTATION_ARTICLE, EXPECTATION_ARTICLE_NAME, HUMAN_EXPIRATION_TIME, true),
          expectation(
              EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, false));
    }
    if (TYPE_CHALLENGE.equals(injectorType)) {
      return arrayOf(
          expectation(
              EXPECTATION_CHALLENGE, EXPECTATION_CHALLENGE_NAME, HUMAN_EXPIRATION_TIME, true),
          expectation(
              EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, false));
    }
    // Default: technical inject (payload-based or external injectors such as openaev/opencti/ovh)
    return arrayOf(
        expectation(
            EXPECTATION_DETECTION,
            EXPECTATION_DETECTION_NAME,
            TECHNICAL_EXPIRATION_TIME,
            true),
        expectation(
            EXPECTATION_PREVENTION,
            EXPECTATION_PREVENTION_NAME,
            TECHNICAL_EXPIRATION_TIME,
            true),
        expectation(
            EXPECTATION_VULNERABILITY,
            EXPECTATION_VULNERABILITY_NAME,
            TECHNICAL_EXPIRATION_TIME,
            true));
  }

  private ArrayNode arrayOf(ObjectNode... nodes) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    for (ObjectNode node : nodes) {
      array.add(node);
    }
    return array;
  }

  private ObjectNode expectation(
      String type, String name, long expirationTime, boolean isLimited) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("expectation_type", type);
    node.put("expectation_name", name);
    node.putNull("expectation_description");
    node.put("expectation_score", DEFAULT_SCORE);
    node.put("expectation_expectation_group", false);
    node.put("expectation_expiration_time", expirationTime);
    node.put("expectation_is_limited", isLimited);
    return node;
  }
}

/*
 * Manual rollback SQL (removes availableExpectations from expectations fields):
 *
 * UPDATE injectors_contracts ic
 * SET injector_contract_content = rollback_content.content::text
 * FROM (
 *   SELECT
 *     injector_contract_id,
 *     tenant_id,
 *     jsonb_set(
 *       injector_contract_content::jsonb,
 *       '{fields}',
 *       (
 *         SELECT jsonb_agg(
 *           CASE
 *             WHEN field->>'key' = 'expectations' THEN field - 'availableExpectations'
 *             ELSE field
 *           END
 *         )
 *         FROM jsonb_array_elements(injector_contract_content::jsonb->'fields') field
 *       ),
 *       false
 *     ) AS content
 *   FROM injectors_contracts
 *   WHERE injector_contract_content IS NOT NULL
 * ) rollback_content
 * WHERE ic.injector_contract_id = rollback_content.injector_contract_id
 *   AND ic.tenant_id = rollback_content.tenant_id;
 */
