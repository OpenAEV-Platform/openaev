package io.openaev.utils.injector_contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.InjectorContract;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Backward-compatibility utility: converts legacy "predefinedExpectations" into
 * "availableExpectations" with "expectation_is_predefined" set to true.
 *
 * <p>Used during imports (starter pack, threat arsenal export) to support older export formats.
 */
public class InjectorContractMigrationUtils {

  private static final String PREDEFINED_EXPECTATIONS_OLD_LIST = "predefinedExpectations";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private InjectorContractMigrationUtils() {}

  /** Converts legacy predefinedExpectations to availableExpectations on the given contract. */
  public static void migratePredefinedExpectations(InjectorContract contract) {
    ObjectNode convertedContent = contract.getConvertedContent();
    if (convertedContent == null) {
      try {
        JsonNode parsed = MAPPER.readTree(contract.getContent());
        if (!(parsed instanceof ObjectNode parsedObject)) {
          return;
        }
        convertedContent = parsedObject;
        contract.setConvertedContent(convertedContent);
      } catch (Exception e) {
        return;
      }
    }
    if (!convertedContent.has("fields")) {
      return;
    }

    convertedContent.put(
        InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_CONTRACT_ID, contract.getId());
    contract.setContent(convertedContent.toString());

    ObjectNode expectationsField =
        (ObjectNode)
            StreamSupport.stream(convertedContent.get("fields").spliterator(), false)
                .filter(f -> "expectations".equals(f.path("key").asText()))
                .findFirst()
                .orElse(null);

    if (expectationsField == null || !expectationsField.has(PREDEFINED_EXPECTATIONS_OLD_LIST)) {
      return;
    }

    ArrayNode available;
    if (expectationsField.has(InjectorContract.AVAILABLE_EXPECTATIONS)
        && expectationsField.get(InjectorContract.AVAILABLE_EXPECTATIONS).isArray()) {
      available = (ArrayNode) expectationsField.get(InjectorContract.AVAILABLE_EXPECTATIONS);
      // Ensure all existing entries have the is_predefined field (default to false)
      for (JsonNode entry : available) {
        if (!entry.has(InjectorContract.IS_PREDEFINED_EXPECTATION)) {
          ((ObjectNode) entry).put(InjectorContract.IS_PREDEFINED_EXPECTATION, false);
        }
      }
    } else {
      available = MAPPER.createArrayNode();
    }

    ArrayNode predefined = (ArrayNode) expectationsField.get(PREDEFINED_EXPECTATIONS_OLD_LIST);
    for (JsonNode exp : predefined) {
      String expType = exp.path("expectation_type").asText();
      Optional<JsonNode> existingOpt =
          StreamSupport.stream(available.spliterator(), false)
              .filter(e -> e.path("expectation_type").asText().equals(expType))
              .findFirst();

      if (existingOpt.isEmpty()) {
        ObjectNode expCopy = exp.deepCopy();
        expCopy.put(InjectorContract.IS_PREDEFINED_EXPECTATION, true);
        available.add(expCopy);
      } else {
        ((ObjectNode) existingOpt.get()).put(InjectorContract.IS_PREDEFINED_EXPECTATION, true);
      }
    }

    expectationsField.set(InjectorContract.AVAILABLE_EXPECTATIONS, available);
    expectationsField.remove(PREDEFINED_EXPECTATIONS_OLD_LIST);
    contract.setContent(convertedContent.toString());
  }
}
