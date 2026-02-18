package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NumberOutputProcessor extends AbstractOutputProcessor {

  private final FindingService findingService;

  public NumberOutputProcessor(FindingService findingService) {
    super(ContractOutputType.Number, ContractOutputTechnicalType.Number, List.of(), true);
    this.findingService = findingService;
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }

  @Override
  public void process(
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
  }
}
