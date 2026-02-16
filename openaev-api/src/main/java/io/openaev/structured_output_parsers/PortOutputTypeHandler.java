package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PortOutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable {

  public PortOutputTypeHandler() {
    super(
        ContractOutputType.Port,
        ContractOutputTechnicalType.Number,
        Set.of(),
        true,
        Set.of(ProcessingContext.FINDING));
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
