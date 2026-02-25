package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputField;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShareOutputProcessor extends AbstractOutputProcessor {

  private static final String ASSET_ID = "asset_id";
  private static final String SHARE_NAME = "share_name";
  private static final String PERMISSIONS = "permissions";
  private static final String HOST = "host";

  private final FindingService findingService;

  public ShareOutputProcessor(FindingService findingService) {
    super(
        ContractOutputType.Share,
        ContractOutputTechnicalType.Object,
        List.of(
            new ContractOutputField(ASSET_ID, ContractOutputTechnicalType.Text, false),
            new ContractOutputField(SHARE_NAME, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(PERMISSIONS, ContractOutputTechnicalType.Text, true),
            new ContractOutputField(HOST, ContractOutputTechnicalType.Text, false)),
        true);
    this.findingService = findingService;
  }

  @Override
  public boolean validate(JsonNode jsonNode) {
    return jsonNode.hasNonNull(SHARE_NAME) && jsonNode.hasNonNull(PERMISSIONS);
  }

  @Override
  public void process(
      ExecutionProcessingContext executionContext,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    findingService.generateFindings(
        executionContext,
        contractOutputContext,
        structuredOutputNode,
        this::validate,
        this::toFindingValue,
        this::toFindingAssets,
        this::toFindingTeams,
        this::toFindingUsers);
  }

  @Override
  public String toFindingValue(JsonNode jsonNode) {
    String shareName = buildString(jsonNode, SHARE_NAME);
    String permissions = buildString(jsonNode, PERMISSIONS);
    String host = buildString(jsonNode, HOST);
    if (!host.isEmpty()) {
      return "\\\\" + host + "\\" + shareName + " (" + permissions + ")";
    }
    return shareName + " (" + permissions + ")";
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
