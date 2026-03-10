package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CredentialsOutputProcessor extends AbstractOutputProcessor {

  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String HASH = "hash";

  public CredentialsOutputProcessor() {
    super(
        ContractOutputType.Credentials,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(USERNAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(PASSWORD, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HASH, ContractOutputTechnicalType.Text, false)),
        true);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(USERNAME)
        && (jsonNode.hasNonNull(PASSWORD) || jsonNode.hasNonNull(HASH));
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String username = buildString(jsonNode, USERNAME);
    String password = buildString(jsonNode, PASSWORD);
    String hash = buildString(jsonNode, HASH);
    if (!hash.isEmpty()) {
      return username + ":" + hash;
    }
    return username + ":" + password;
  }
}
