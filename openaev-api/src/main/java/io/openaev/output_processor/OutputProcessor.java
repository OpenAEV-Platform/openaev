package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.List;

/**
 * Handler interface for processing structured outputs in different contexts. Implementations of
 * this interface will define how to validate and process structured outputs based on their type and
 * technical type, as well as the contexts they support.
 */
public interface OutputProcessor {

  /** Get the type (matches ContractOutputType enum) */
  ContractOutputType getType();

  /** Get the technical type (matches ContractOutputTechnicalType enum) */
  ContractOutputTechnicalType getTechnicalType();

  /** Get fields */
  List<ContractOutputField> getFields();

  /** Validate that the JSON node is correctly formatted for this type */
  boolean validate(JsonNode jsonNode);

  /**
   * Process a set of operations like generating findings, matching expectations and process assets.
   */
  void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode);
}
