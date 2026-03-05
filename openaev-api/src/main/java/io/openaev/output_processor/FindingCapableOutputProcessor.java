package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/** Abstract base class for output processors that are capable of generating findings. */
@Slf4j
public abstract class FindingCapableOutputProcessor extends AbstractOutputProcessor {

  protected final FindingService findingService;

  protected FindingCapableOutputProcessor(
      ContractOutputType type,
      ContractOutputTechnicalType technicalType,
      List<ContractOutputField> fields,
      FindingService findingService) {
    super(type, technicalType, fields);
    this.findingService = findingService;
  }

  /**
   * Processes the structured output by generating findings via {@link FindingService}, then calls
   * {@link #afterFindings} to allow subclasses to perform additional steps (e.g. expectation
   * matching) without overriding this method entirely.
   */
  @Override
  public final void process(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    findingService.generateFindings(
        executionContext,
        contractOutputContext,
        structuredOutputNode,
        this::validate,
        this::toFindingValue,
        this::toFindingAssets,
        this::toFindingTeams,
        this::toFindingUsers);
    afterFindings(executionContext, structuredOutputNode);
  }

  /**
   * Hook called after findings are generated. Override to perform additional processing such as
   * expectation matching without needing to override {@link #process} entirely.
   */
  protected void afterFindings(
      ExecutionProcessingContext executionContext, JsonNode structuredOutputNode) {
    // no-op by default
  }

  /** Convert JSON node to finding value string. Subclasses must provide a meaningful value. */
  public abstract String toFindingValue(JsonNode jsonNode);

  /** Extract asset IDs from JSON node. Default returns empty list. */
  public List<String> toFindingAssets(JsonNode jsonNode) {
    log.debug("Processor {} does not implement toFindingAssets, returning empty list", type);
    return Collections.emptyList();
  }

  /** Extract user IDs from JSON node. Default returns empty list. */
  public List<String> toFindingUsers(JsonNode jsonNode) {
    log.debug("Processor {} does not implement toFindingUsers, returning empty list", type);
    return Collections.emptyList();
  }

  /** Extract team IDs from JSON node. Default returns empty list. */
  public List<String> toFindingTeams(JsonNode jsonNode) {
    log.debug("Processor {} does not implement toFindingTeams, returning empty list", type);
    return Collections.emptyList();
  }
}
