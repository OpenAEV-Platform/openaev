package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CVEOutputProcessor extends AbstractOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String ID = "id";
  private static final String HOST = "host";
  private static final String SEVERITY = "severity";

  public CVEOutputProcessor() {
    super(
        ContractOutputType.CVE,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(ID, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(SEVERITY, ContractOutputTechnicalType.Text, true)),
        true);
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
  public List<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    if (assetIdNode == null) {
      return Collections.emptyList();
    }
    if (assetIdNode.isArray()) {
      List<String> result = new ArrayList<>();
      for (JsonNode idNode : assetIdNode) {
        result.add(idNode.asText());
      }
      return result;
    }
    return List.of(assetIdNode.asText());
  }
}
