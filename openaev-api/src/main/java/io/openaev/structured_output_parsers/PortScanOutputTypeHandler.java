package io.openaev.structured_output_parsers;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PortScanOutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable {

  public PortScanOutputTypeHandler() {
    super(
        ContractOutputType.PortsScan,
        ContractOutputTechnicalType.Object,
        Set.of(
            new ContractOutputField("asset_id", ContractOutputTechnicalType.Text, false),
            new ContractOutputField("host", ContractOutputTechnicalType.Text, true),
            new ContractOutputField("port", ContractOutputTechnicalType.Number, true),
            new ContractOutputField("service", ContractOutputTechnicalType.Text, true)),
        true,
        Set.of(ProcessingContext.FINDING));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull("host")
        && jsonNode.hasNonNull("port")
        && jsonNode.hasNonNull("service");
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String host = buildString(jsonNode, "host");
    String port = buildString(jsonNode, "port");
    String service = buildString(jsonNode, "service");
    return host + ":" + port + (hasText(service) ? " (" + service + ")" : "");
  }

  @Override
  public Set<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get("asset_id");
    if (assetIdNode != null) {
      return Set.of(assetIdNode.asText());
    }
    return Set.of();
  }
}
