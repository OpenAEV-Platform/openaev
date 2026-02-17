package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TextOutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable {

  public TextOutputTypeHandler() {
    super(
        "text",
        ContractOutputType.Text,
        ContractOutputTechnicalType.Text,
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
