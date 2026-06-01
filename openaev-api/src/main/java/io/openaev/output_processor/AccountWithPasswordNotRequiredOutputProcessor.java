package io.openaev.output_processor;

import static org.springframework.util.StringUtils.hasText;

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
public class AccountWithPasswordNotRequiredOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String ACCOUNT = "account";
  private static final String STATUS = "status";
  private static final String HOST = "host";

  public AccountWithPasswordNotRequiredOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.AccountWithPasswordNotRequired,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(ACCOUNT, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(STATUS, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(ACCOUNT);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String account = buildString(jsonNode, ACCOUNT);
    String status = buildString(jsonNode, STATUS);
    if (hasText(status)) {
      return account + " (" + status + ")";
    }
    return account;
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
