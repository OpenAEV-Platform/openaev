package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PortOutputProcessor extends FindingCapableOutputProcessor {

  public PortOutputProcessor(FindingService findingService) {
    super(ContractOutputType.Port, ContractOutputTechnicalType.Number, List.of(), findingService);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
