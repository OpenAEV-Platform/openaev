package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.stereotype.Component;

@Component
public class IPv6OutputProcessorHandler extends AbstractStructuredOutputProcessorHandler
    implements FindingCapable {

  private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

  public IPv6OutputProcessorHandler() {
    super(
        "ipv6",
        ContractOutputType.IPv6,
        ContractOutputTechnicalType.Text,
        Set.of(),
        true,
        Set.of(ProcessingContext.FINDING));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return VALIDATOR.isValidInet6Address(jsonNode.asText());
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    return buildString(jsonNode);
  }
}
