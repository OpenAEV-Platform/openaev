package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Collections;
import java.util.List;

/**
 * Handler interface for processing structured outputs in different contexts. Implementations of
 * this interface will define how to validate and process structured outputs based on their type and
 * technical type, as well as the contexts they support.
 */
public interface OutputProcessorHandler {

  /** Get the type (matches ContractOutputType enum) */
  ContractOutputType getType();

  /** Get the technical type (matches ContractOutputTechnicalType enum) */
  ContractOutputTechnicalType getTechnicalType();

  /** Get fields */
  List<ContractOutputField> getFields();

  /** Is finding compatible */
  boolean isFindingCompatible();

  /** Validate that the JSON node is correctly formatted for this type */
  boolean validate(JsonNode jsonNode);

  // Findings Processing

  /** Convert JSON node to finding value string */
  String toFindingValue(JsonNode jsonNode);

  /**
   * Extract asset IDs from JSON node for finding linking. Default implementation returns empty
   * list.
   */
  default List<String> toFindingAssets(JsonNode jsonNode) {
    return Collections.emptyList();
  }

  /**
   * Extract user IDs from JSON node for finding linking. Default implementation returns empty list.
   */
  default List<String> toFindingUsers(JsonNode jsonNode) {
    return Collections.emptyList();
  }

  /**
   * Extract team IDs from JSON node for finding linking. Default implementation returns empty list.
   */
  default List<String> toFindingTeams(JsonNode jsonNode) {
    return Collections.emptyList();
  }

  // Asset Processing

  /** Find or Create Asset from jsonNode */
  Asset toAsset(JsonNode jsonNode);

  // Expectation Validation

  /**
   * Check if actual JSON value matches the expectation
   *
   * @param jsonNode the actual value from output
   * @param expectation the expected value/condition to match against
   * @return true if expectation is met, false otherwise
   */
  boolean matchesExpectation(JsonNode jsonNode, JsonNode expectation);
}
