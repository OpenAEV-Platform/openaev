package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CredentialsOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String HASH = "hash";
  private static final String HOST = "host";

  public CredentialsOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.Credentials,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(USERNAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(PASSWORD, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(HASH, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(USERNAME)
        && (jsonNode.hasNonNull(PASSWORD) || jsonNode.hasNonNull(HASH));
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String username = buildString(jsonNode, USERNAME);
    if (jsonNode.hasNonNull(PASSWORD)) {
      return username + ":" + buildString(jsonNode, PASSWORD);
    }
    return username + ":" + buildString(jsonNode, HASH);
  }

  @Override
  public List<String> toFindingAssets(JsonNode jsonNode) {
    JsonNode assetIdNode = jsonNode.get(ASSET_ID);
    if (assetIdNode == null) {
      return Collections.emptyList();
    }
    if (assetIdNode.isArray()) {
      List<String> result = new ArrayList<>();
      for (JsonNode idNode : assetIdNode) {
        result.add(idNode.asText());
      }
      return result;
    }
    return List.of(assetIdNode.asText());
  }
}
