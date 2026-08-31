package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.expectation.ExpectationSignature;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.service.InjectExpectationService;
import java.util.*;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class SignatureOutputProcessor extends AbstractOutputProcessor {

  private static final String TARGETS = "targets";
  private static final String SIGNATURE_TARGET = "signature_target";
  private static final String SIGNATURE_VALUES = "signature_values";
  private static final String EXPECTATION_TYPE = "expectation_type";
  private static final String VALUES = "values";
  private static final String SIGNATURE_TYPE = "signature_type";
  private static final String SIGNATURE_VALUE = "signature_value";

  private final InjectExpectationService injectExpectationService;

  public SignatureOutputProcessor(InjectExpectationService injectExpectationService) {
    super(ContractOutputType.ExpectationSignature, ContractOutputTechnicalType.Object, List.of());
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  public void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    JsonNode targetsNode = structuredOutputNode.path(TARGETS);
    if (!targetsNode.isArray()) {
      return;
    }

    String injectId = ctx.inject().getId();
    for (JsonNode targetNode : targetsNode) {
      JsonNode signatureTargetNode = targetNode.path(SIGNATURE_TARGET);
      String agentId = readText(signatureTargetNode, "agent_id", "agent");
      String assetId = readText(signatureTargetNode, "asset_id", "asset");
      String assetGroupId = readText(signatureTargetNode, "asset_group_id", "asset_group");

      JsonNode signatureValuesNode = targetNode.path(SIGNATURE_VALUES);
      if (!signatureValuesNode.isArray()) {
        continue;
      }

      for (JsonNode signatureValueNode : signatureValuesNode) {
        Optional<BaseInjectExpectation.EXPECTATION_TYPE> expectationType =
            mapExpectationType(signatureValueNode.path(EXPECTATION_TYPE).asText(null));
        if (expectationType.isEmpty()) {
          continue;
        }

        List<ExpectationSignature> signatures = extractSignatures(signatureValueNode.path(VALUES));
        injectExpectationService.appendExpectationSignatures(
            injectId, agentId, assetId, assetGroupId, expectationType.get(), signatures);
      }
    }
  }

  private Optional<BaseInjectExpectation.EXPECTATION_TYPE> mapExpectationType(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          BaseInjectExpectation.EXPECTATION_TYPE.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private List<ExpectationSignature> extractSignatures(JsonNode valuesNode) {
    if (!valuesNode.isArray()) {
      return new ArrayList<>();
    }
    return StreamSupport.stream(valuesNode.spliterator(), false)
        .map(
            valueNode ->
                new ExpectationSignature(
                    readText(valueNode, SIGNATURE_TYPE, "type"),
                    readText(valueNode, SIGNATURE_VALUE, "value")))
        .filter(
            signature ->
                StringUtils.hasText(signature.getType())
                    && StringUtils.hasText(signature.getValue()))
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  private String readText(JsonNode node, String... keys) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    return Arrays.stream(keys)
        .map(node::get)
        .filter(Objects::nonNull)
        .filter(JsonNode::isValueNode)
        .map(JsonNode::asText)
        .filter(StringUtils::hasText)
        .findFirst()
        .orElse(null);
  }
}
