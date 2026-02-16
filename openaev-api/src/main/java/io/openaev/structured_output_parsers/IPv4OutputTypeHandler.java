package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class IPv4OutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable {

  public IPv4OutputTypeHandler() {
    super("text", Set.of(ProcessingContext.FINDING));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode != null;
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
