package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CVEOutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable, ExpectationCapable {

  public CVEOutputTypeHandler() {
    super(
        ContractOutputType.CVE,
        ContractOutputTechnicalType.Object,
        Set.of(
            new ContractOutputField("asset_id", ContractOutputTechnicalType.Text, false),
            new ContractOutputField("id", ContractOutputTechnicalType.Text, true),
            new ContractOutputField("host", ContractOutputTechnicalType.Text, true),
            new ContractOutputField("severity", ContractOutputTechnicalType.Text, true)),
        true,
        Set.of(ProcessingContext.FINDING, ProcessingContext.EXPECTATION));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull("id")
        && jsonNode.hasNonNull("host")
        && jsonNode.hasNonNull("severity");
  }

  // Findings
  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode, "id");
  }

  @Override
  public Set<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get("asset_id");
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
