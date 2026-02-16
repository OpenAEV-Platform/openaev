package io.openaev.output_processor;

import static org.springframework.util.StringUtils.hasText;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PortScanOutputProcessor extends AbstractOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String HOST = "host";
  private static final String PORT = "port";
  private static final String SERVICE = "service";

  public PortScanOutputProcessor() {
    super(
        ContractOutputType.PortsScan,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(PORT, ContractOutputTechnicalType.Number, true),
            new ContractOutputField(SERVICE, ContractOutputTechnicalType.Text, true)),
        true);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(HOST) && jsonNode.hasNonNull(PORT) && jsonNode.hasNonNull(SERVICE);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String host = buildString(jsonNode, HOST);
    String port = buildString(jsonNode, PORT);
    String service = buildString(jsonNode, SERVICE);
    return host + ":" + port + (hasText(service) ? " (" + service + ")" : "");
  }

  @Override
  public List<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    if (assetIdNode != null) {
      return List.of(assetIdNode.asText());
    }
    return List.of();
  }
}
