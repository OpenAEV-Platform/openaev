package io.openaev.structured_output_parsers;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.stereotype.Component;

@Component
public class IPv4OutputTypeHandler extends AbstractContractOutputTypeHandler
    implements FindingCapable {

  private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

  public IPv4OutputTypeHandler() {
    super(
        ContractOutputType.IPv4,
        ContractOutputTechnicalType.Text,
        Set.of(),
        true,
        Set.of(ProcessingContext.FINDING));
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
