package io.openaev.api.chaining;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.dto.MapperConditionOutput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.ConditionStep;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Step;
import java.util.List;

/** Mapper for Step template API DTOs. */
public final class StepMapper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private StepMapper() {}

  public static StepOutput toOutput(Step step) {
    try {
      List<String> rootConditionIds =
          step.getConditionSteps().stream()
              .filter(ConditionStep::isRoot)
              .filter(cs -> cs.getCondition().getType() != ConditionType.MAPPER)
              .map(cs -> cs.getCondition().getId())
              .toList();

      // Extract mapper conditions linked to this step
      List<MapperConditionOutput> mapperConditions =
          step.getConditionSteps().stream()
              .map(ConditionStep::getCondition)
              .filter(c -> c.getType() == ConditionType.MAPPER)
              .map(
                  c ->
                      MapperConditionOutput.builder()
                          .conditionKeyType(c.getKeyType())
                          .conditionKey(c.getKey())
                          .conditionValue(c.getValue())
                          .conditionMappingType(c.getMappingType())
                          .build())
              .toList();

      JsonNode dataNode = step.getData() == null ? null : OBJECT_MAPPER.readTree(step.getData());
      List<String> outputTypes = extractOutputTypes(dataNode);

      return StepOutput.builder()
          .id(step.getId())
          .status(step.getStatus())
          .conditionIds(rootConditionIds)
          .mapperConditions(mapperConditions)
          .conditionKeyTypes(step.getConditionKeyTypes())
          .outputTypes(outputTypes)
          .data(dataNode)
          .createdAt(step.getCreatedAt())
          .updatedAt(step.getUpdatedAt())
          .build();
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to parse step data as JSON", e);
    }
  }

  /**
   * Extracts output types from the step_data JSON tree.
   *
   * <p>Primary source: inject_injector_contract → injector_contract_providing[]. This field is
   * computed by {@link io.openaev.database.model.InjectorContract#getProviding()} and already
   * covers both payload-backed contracts and native injectors that declare their outputs directly
   * in the contract content ("outputs" array).
   *
   * <p>Fallback (legacy step data): inject_injector_contract → injector_contract_payload →
   * payload_output_parsers[] → output_parser_contract_output_elements[] →
   * contract_output_element_type
   *
   * <p>todo: refacto in primitive type
   */
  private static List<String> extractOutputTypes(JsonNode dataNode) {
    if (dataNode == null) return List.of();
    JsonNode contract = dataNode.path("inject_injector_contract");
    if (contract.isMissingNode()) return List.of();

    JsonNode providing = contract.path("injector_contract_providing");
    if (providing.isArray() && !providing.isEmpty()) {
      List<String> result = new java.util.ArrayList<>();
      for (JsonNode type : providing) {
        String value = type.asText(null);
        if (value != null && !value.isBlank()) result.add(value);
      }
      return result;
    }

    JsonNode parsers = contract.path("injector_contract_payload").path("payload_output_parsers");
    if (!parsers.isArray()) return List.of();

    List<String> result = new java.util.ArrayList<>();
    for (JsonNode parser : parsers) {
      JsonNode elements = parser.path("output_parser_contract_output_elements");
      if (!elements.isArray()) continue;
      for (JsonNode el : elements) {
        String type = el.path("contract_output_element_type").asText(null);
        if (type != null && !type.isBlank()) result.add(type);
      }
    }
    return result;
  }
}
