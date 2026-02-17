package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;

/**
 * Handler interface for processing contract output types in different contexts. This interface
 * separates the processing logic from the enum definition.
 */
public interface ContractOutputTypeHandler {

  /** Get the label (matches ContractOutputType enum) */
  String getLabel();

  /** Get the type (matches ContractOutputType enum) */
  ContractOutputType getType();

  /** Get the technical type (matches ContractOutputTechnicalType enum) */
  ContractOutputTechnicalType getTechnicalType();

  /** Get fields */
  Set<ContractOutputField> getFields();

  /** Is finding compatible */
  boolean isFindingCompatible();

  /** Get the supported processing contexts for this handler */
  Set<ProcessingContext> getSupportedContexts();

  /** Check if this handler supports a specific context */
  default boolean supportsContext(ProcessingContext context) {
    return getSupportedContexts().contains(context);
  }

  /** Validate that the JSON node is correctly formatted for this type */
  boolean validate(JsonNode jsonNode);
}
