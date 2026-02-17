package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.springframework.stereotype.Component;

@Component
public class IPv6OutputProcessorHandler extends AbstractOutputProcessorHandler
    implements FindingCapable {

  private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

  public IPv6OutputProcessorHandler() {
    super(
        ContractOutputType.IPv6,
        ContractOutputTechnicalType.Text,
        List.of(),
        true,
        List.of(ProcessingContext.FINDING));
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
