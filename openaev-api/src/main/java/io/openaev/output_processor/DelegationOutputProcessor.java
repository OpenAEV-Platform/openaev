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
public class DelegationOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String ACCOUNT = "account";
  private static final String DELEGATION_TYPE = "delegation_type";
  private static final String RIGHTS_TO = "rights_to";
  private static final String HOST = "host";

  public DelegationOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.Delegation,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(ACCOUNT, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(DELEGATION_TYPE, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(RIGHTS_TO, ContractOutputTechnicalType.Text, false),
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
    String delegationType = buildString(jsonNode, DELEGATION_TYPE);
    String rightsTo = buildString(jsonNode, RIGHTS_TO);
    StringBuilder sb = new StringBuilder(account);
    if (hasText(delegationType)) {
      sb.append(" [").append(delegationType).append("]");
    }
    if (hasText(rightsTo)) {
      sb.append(" -> ").append(rightsTo);
    }
    return sb.toString();
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
