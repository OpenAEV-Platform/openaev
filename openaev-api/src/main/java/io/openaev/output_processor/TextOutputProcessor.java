package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextOutputProcessor extends AbstractOutputProcessor {

  public TextOutputProcessor() {
    super(ContractOutputType.Text, ContractOutputTechnicalType.Text, List.of(), true);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
