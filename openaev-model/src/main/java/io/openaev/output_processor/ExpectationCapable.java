package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Interface for handlers that can validate expectations. Only handlers that support
 * ProcessingContext.EXPECTATION should implement this.
 */
public interface ExpectationCapable {

  /**
   * Check if actual JSON value matches the expectation
   *
   * @param jsonNode the actual value from output
   * @param expectation the expected value/condition to match against
   * @return true if expectation is met, false otherwise
   */
  boolean matchesExpectation(JsonNode jsonNode, JsonNode expectation);
}
