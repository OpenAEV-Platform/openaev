package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SidOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String SID = "sid";
  private static final String HOST = "host";

  public SidOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.Sid,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(SID, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(SID);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode, SID);
  }
}
