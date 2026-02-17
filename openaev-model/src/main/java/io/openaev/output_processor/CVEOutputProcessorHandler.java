package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CVEOutputProcessorHandler extends AbstractOutputProcessorHandler
    implements FindingCapable, ExpectationCapable {

  private static final String ASSET_ID = "asset_id";
  private static final String ID = "id";
  private static final String HOST = "host";
  private static final String SEVERITY = "severity";

  public CVEOutputProcessorHandler() {
    super(
        ContractOutputType.CVE,
        ContractOutputTechnicalType.Object,
        Set.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(ID, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(SEVERITY, ContractOutputTechnicalType.Text, true)),
        true,
        Set.of(ProcessingContext.FINDING, ProcessingContext.EXPECTATION));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(ID) && jsonNode.hasNonNull(HOST) && jsonNode.hasNonNull(SEVERITY);
  }

  // Findings
  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode, ID);
  }

  @Override
  public Set<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    if (assetIdNode == null) {
      return Collections.emptySet();
    }
    if (assetIdNode.isArray()) {
      Set<String> result = new HashSet<>();
      for (JsonNode idNode : assetIdNode) {
        result.add(idNode.asText());
      }
      return result;
    }
    return Set.of(assetIdNode.asText());
  }

  // Expectations
  @Override
  public boolean matchesExpectation(JsonNode jsonNode, JsonNode expectation) {
    return false;
  }
}
