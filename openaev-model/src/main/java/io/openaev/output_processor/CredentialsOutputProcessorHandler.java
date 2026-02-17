package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CredentialsOutputProcessorHandler extends AbstractOutputProcessorHandler
    implements FindingCapable {

  public CredentialsOutputProcessorHandler() {
    super(
        ContractOutputType.Credentials,
        ContractOutputTechnicalType.Object,
        Set.of(
            new ContractOutputField("username", ContractOutputTechnicalType.Text, true),
            new ContractOutputField("password", ContractOutputTechnicalType.Text, true)),
        true,
        Set.of(ProcessingContext.FINDING));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull("username") && jsonNode.hasNonNull("password");
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String username = buildString(jsonNode, "username");
    String password = buildString(jsonNode, "password");
    return username + ":" + password;
  }
}
