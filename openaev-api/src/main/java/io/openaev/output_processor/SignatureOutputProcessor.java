package io.openaev.output_processor;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.ContractOutputTechnicalType;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.PreviewFeatureService;
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
  private final PreviewFeatureService previewFeatureService;

  public SignatureOutputProcessor(
      InjectExpectationService injectExpectationService,
      PreviewFeatureService previewFeatureService) {
    super(ContractOutputType.ExpectationSignature, ContractOutputTechnicalType.Object, List.of());
    this.injectExpectationService = injectExpectationService;
    this.previewFeatureService = previewFeatureService;
  }

  @Override
  public void process(
      ExecutionProcessingContext ctx,
      ContractOutputContext contractOutputContext,
      JsonNode structuredOutputNode) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.SIGNATURE_OUTPUT_PROCESSOR)) {
      log.debug("Signature processing disabled");
      return;
    }

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
        Optional<InjectExpectation.EXPECTATION_TYPE> expectationType =
            mapExpectationType(signatureValueNode.path(EXPECTATION_TYPE).asText(null));
        if (expectationType.isEmpty()) {
          continue;
        }

        List<InjectExpectationSignature> signatures =
            extractSignatures(signatureValueNode.path(VALUES));
        injectExpectationService.appendExpectationSignatures(
            injectId, agentId, assetId, assetGroupId, expectationType.get(), signatures);
      }
    }
  }

  private Optional<InjectExpectation.EXPECTATION_TYPE> mapExpectationType(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          InjectExpectation.EXPECTATION_TYPE.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private List<InjectExpectationSignature> extractSignatures(JsonNode valuesNode) {
    if (!valuesNode.isArray()) {
      return List.of();
    }
    return StreamSupport.stream(valuesNode.spliterator(), false)
        .map(
            valueNode ->
                new InjectExpectationSignature(
                    readText(valueNode, SIGNATURE_TYPE, "type"),
                    readText(valueNode, SIGNATURE_VALUE, "value")))
        .filter(
            signature ->
                StringUtils.hasText(signature.getType())
                    && StringUtils.hasText(signature.getValue()))
        .toList();
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
