package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.stereotype.Component;

@Component
public class IPv4OutputProcessor extends AbstractOutputProcessor {

  private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

  public IPv4OutputProcessor() {
    super(ContractOutputType.IPv4, ContractOutputTechnicalType.Text, List.of(), true);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return VALIDATOR.isValidInet4Address(jsonNode.asText());
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
