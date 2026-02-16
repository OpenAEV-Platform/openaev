package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/**
 * Handler interface for processing contract output types in different contexts. This interface
 * separates the processing logic from the enum definition.
 */
public interface ContractOutputTypeHandler {

  /** Get the type label (matches ContractOutputType enum label) */
  String getLabel();

  /** Get the supported processing contexts for this handler */
  Set<ProcessingContext> getSupportedContexts();

  /** Check if this handler supports a specific context */
  default boolean supportsContext(ProcessingContext context) {
    return getSupportedContexts().contains(context);
  }

  /** Validate that the JSON node is correctly formatted for this type */
  boolean validate(JsonNode jsonNode);
}
