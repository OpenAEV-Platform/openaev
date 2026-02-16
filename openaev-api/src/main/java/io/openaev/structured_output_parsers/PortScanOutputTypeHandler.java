package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PortScanOutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable {

  public PortsScanOutputTypeHandler() {
    super("portscan", Set.of(ProcessingContext.FINDING));
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
    return host + ":" + port + (StringUtils.hasText(service) ? " (" + service + ")" : "");
  }

  @Override
  public List<String> toFindingAssets(JsonNode jsonNode) {
    if (jsonNode.get("asset_id") != null) {
      return List.of(jsonNode.get("asset_id").asText());
    }
    return Collections.emptyList();
  }
}
