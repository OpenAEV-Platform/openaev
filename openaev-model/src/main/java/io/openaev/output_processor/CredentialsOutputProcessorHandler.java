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

  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";

  public CredentialsOutputProcessorHandler() {
    super(
        ContractOutputType.Credentials,
        ContractOutputTechnicalType.Object,
        Set.of(
            new ContractOutputField(USERNAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(PASSWORD, ContractOutputTechnicalType.Text, true)),
        true,
        Set.of(ProcessingContext.FINDING));
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(USERNAME) && jsonNode.hasNonNull(PASSWORD);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String username = buildString(jsonNode, USERNAME);
    String password = buildString(jsonNode, PASSWORD);
    return username + ":" + password;
  }
}
