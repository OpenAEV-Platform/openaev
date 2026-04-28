package io.openaev.datapack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Normalizes {@code availableExpectations} on all injector contracts after the application has
 * fully started.
 *
 * <p>This complements the Flyway migration {@code V4_93} which handles data existing at migration
 * time. Contracts created by DataPacks or built-in injectors (email, channel, challenge, manual,
 * payload-based) are inserted during Spring startup — after Flyway — so they cannot be covered by
 * the migration alone. This listener runs after all {@code @PostConstruct} methods (including
 * {@link DataPackProcessor}), ensuring every contract has a correct {@code availableExpectations}.
 *
 * <p>The operation is idempotent: only contracts that differ from the expected value are updated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("!test")
public class InjectorContractAvailableExpectationsNormalizer {

  private final DataSource dataSource;

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
  private static final String EXPECTATION_ARTICLE = "ARTICLE";
  private static final String EXPECTATION_CHALLENGE = "CHALLENGE";
  private static final String EXPECTATION_DETECTION = "DETECTION";
  private static final String EXPECTATION_PREVENTION = "PREVENTION";
  private static final String EXPECTATION_VULNERABILITY = "VULNERABILITY";

  @EventListener(ApplicationReadyEvent.class)
  public void normalizeOnStartup() {
    log.info("Normalizing availableExpectations on all injector contracts...");
    try {
      int updated = normalize();
      log.info("Normalized availableExpectations on {} injector contract(s).", updated);
    } catch (Exception e) {
      log.error("Failed to normalize availableExpectations on injector contracts.", e);
    }
  }

  private int normalize() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    int updatedCount = 0;

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

    try (Connection conn = dataSource.getConnection();
        PreparedStatement select = conn.prepareStatement(selectQuery);
        ResultSet rs = select.executeQuery();
        PreparedStatement update =
            conn.prepareStatement(
                "UPDATE injectors_contracts "
                    + "SET injector_contract_content = ? "
                    + "WHERE injector_contract_id = ? AND tenant_id = ?")) {

      while (rs.next()) {
        if (processContract(mapper, rs, update)) {
          updatedCount++;
        }
      }
      update.executeBatch();
    }
    return updatedCount;
  }

  private boolean processContract(ObjectMapper mapper, ResultSet rs, PreparedStatement update)
      throws Exception {
    String contractId = rs.getString("injector_contract_id");
    String tenantId = rs.getString("tenant_id");
    String content = rs.getString("injector_contract_content");
    boolean isManual = rs.getBoolean("injector_contract_manual");
    String injectorType = rs.getString("injector_type");

    ObjectNode contractContent = (ObjectNode) mapper.readTree(content);
    if (contractContent == null
        || !contractContent.has(FIELDS)
        || !contractContent.get(FIELDS).isArray()) {
      return false;
    }

    ArrayNode fields = (ArrayNode) contractContent.get(FIELDS);
    boolean modified = normalizeExpectationsField(fields, isManual, injectorType);

    if (modified) {
      update.setString(1, mapper.writeValueAsString(contractContent));
      update.setString(2, contractId);
      update.setString(3, tenantId);
      update.addBatch();
    }
    return modified;
  }

  private boolean normalizeExpectationsField(
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

      boolean needsUpdate =
          current == null
              || current.isNull()
              || !current.isArray()
              || !normalizeIsLimitedFlags((ArrayNode) current).equals(current);

      if (needsUpdate) {
        field.set(AVAILABLE_EXPECTATIONS, normalizeOrBuild(current, expected));
        modified = true;
      }
    }
    return modified;
  }

  /** If a non-null array already exists, repair its is_limited flags. Otherwise use expected. */
  private ArrayNode normalizeOrBuild(JsonNode current, ArrayNode expected) {
    if (current != null && !current.isNull() && current.isArray()) {
      return normalizeIsLimitedFlags((ArrayNode) current);
    }
    return expected;
  }

  private ArrayNode normalizeIsLimitedFlags(ArrayNode array) {
    ArrayNode normalized = array.deepCopy();
    for (JsonNode node : normalized) {
      if (!(node instanceof ObjectNode exp)) {
        continue;
      }
      String type = exp.path("expectation_type").asText(null);
      if (type != null) {
        exp.put("expectation_is_limited", EXPECTATION_MANUAL.equals(type));
      }
    }
    return normalized;
  }

  /**
   * Builds the canonical availableExpectations for a given contract type.
   *
   * <ul>
   *   <li>Manual → [MANUAL (limited)]
   *   <li>Email → [TEXT, MANUAL (limited)]
   *   <li>Channel → [ARTICLE, MANUAL (limited)]
   *   <li>Challenge → [CHALLENGE, MANUAL (limited)]
   *   <li>Technical (payload / external) → [DETECTION, PREVENTION, VULNERABILITY]
   * </ul>
   */
  private ArrayNode buildAvailableExpectations(boolean isManual, String injectorType) {
    if (isManual || TYPE_MANUAL.equals(injectorType)) {
      return arrayOf(expectation(EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, true));
    }
    if (TYPE_EMAIL.equals(injectorType)) {
      return arrayOf(
          expectation(EXPECTATION_TEXT, "Simple expectation", HUMAN_EXPIRATION_TIME, false),
          expectation(EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, true));
    }
    if (TYPE_CHANNEL.equals(injectorType)) {
      return arrayOf(
          expectation(EXPECTATION_ARTICLE, "Expect targets to read the article(s)", HUMAN_EXPIRATION_TIME, false),
          expectation(EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, true));
    }
    if (TYPE_CHALLENGE.equals(injectorType)) {
      return arrayOf(
          expectation(EXPECTATION_CHALLENGE, "Expect targets to complete the challenge(s)", HUMAN_EXPIRATION_TIME, false),
          expectation(EXPECTATION_MANUAL, EXPECTATION_MANUAL_NAME, HUMAN_EXPIRATION_TIME, true));
    }
    // Default: technical (payload-based or external injectors: openaev, opencti, ovh…)
    return arrayOf(
        expectation(EXPECTATION_DETECTION, "Detection", TECHNICAL_EXPIRATION_TIME, false),
        expectation(EXPECTATION_PREVENTION, "Prevention", TECHNICAL_EXPIRATION_TIME, false),
        expectation(EXPECTATION_VULNERABILITY, "Vulnerability", TECHNICAL_EXPIRATION_TIME, false));
  }

  private ArrayNode arrayOf(ObjectNode... nodes) {
    ArrayNode array = JsonNodeFactory.instance.arrayNode();
    for (ObjectNode node : nodes) {
      array.add(node);
    }
    return array;
  }

  private ObjectNode expectation(String type, String name, long expirationTime, boolean isLimited) {
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

