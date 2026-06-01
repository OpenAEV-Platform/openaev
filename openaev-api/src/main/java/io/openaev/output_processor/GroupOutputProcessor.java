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
public class GroupOutputProcessor extends FindingCapableOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String GROUP_NAME = "group_name";
  private static final String MEMBER_COUNT = "member_count";
  private static final String RID = "rid";
  private static final String HOST = "host";

  public GroupOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.Group,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(GROUP_NAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(MEMBER_COUNT, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(RID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        findingService);
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(GROUP_NAME);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String groupName = buildString(jsonNode, GROUP_NAME);
    String memberCount = buildString(jsonNode, MEMBER_COUNT);
    if (hasText(memberCount)) {
      return groupName + " (" + memberCount + " members)";
    }
    return groupName;
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
