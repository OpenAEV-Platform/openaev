package io.openaev.service.chaining;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.chaining.DataInputStep;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.MappingType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Derives the default MAPPER conditions ("auto-links") of a step from its injector contract, so
 * that steps created directly through the API get the same links as steps created from the action
 * form.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StepAutoLinkService {

  private static final String CONTRACT_FIELDS_NODE = "fields";
  private static final String CONTRACT_FIELD_KEY = "key";
  private static final String CONTRACT_FIELD_TYPE = "type";
  private static final String CONTRACT_FIELD_ARGUMENT_TYPE = "argumentType";

  private final InjectorContractService injectorContractService;
  private final ObjectMapper mapper;

  /**
   * Builds one global MAPPER condition per injector contract field exposing an argument type.
   *
   * @param dataStep step data holding the injector contract to read
   * @return the auto-link conditions, empty when the contract exposes no argument type
   */
  public List<ConditionCreateInput> buildAutoLinkConditions(DataInputStep dataStep) {
    if (!(dataStep instanceof InjectInput injectInput)
        || !StringUtils.hasText(injectInput.getInjectorContract())) {
      return List.of();
    }

    List<ConditionCreateInput> conditions = new ArrayList<>();
    for (JsonNode field : contractFields(injectInput.getInjectorContract())) {
      Optional<PrimitiveType> keyType = resolveAutoLinkType(field);
      JsonNode keyNode = field.get(CONTRACT_FIELD_KEY);
      if (keyType.isEmpty() || keyNode == null || !keyNode.isTextual()) {
        continue;
      }
      conditions.add(
          ConditionCreateInput.builder()
              .temporaryId(String.valueOf(conditions.size()))
              .type(ConditionType.MAPPER)
              .mappingType(MappingType.GLOBAL)
              .key(keyNode.asText())
              .keyTypes(List.of(keyType.get()))
              .build());
    }
    return conditions;
  }

  private Iterable<JsonNode> contractFields(String injectorContractId) {
    InjectorContract injectorContract;
    try {
      injectorContract = injectorContractService.injectorContract(injectorContractId);
    } catch (ElementNotFoundException e) {
      // Auto-link is best-effort: an unknown contract is reported by the regular creation flow.
      return List.of();
    }
    try {
      JsonNode fields = mapper.readTree(injectorContract.getContent()).get(CONTRACT_FIELDS_NODE);
      return fields != null && fields.isArray() ? fields : List.of();
    } catch (JsonProcessingException e) {
      log.warn("Unreadable content for injector contract {}", injectorContractId, e);
      return List.of();
    }
  }

  private static Optional<PrimitiveType> resolveAutoLinkType(JsonNode field) {
    JsonNode argumentType = field.get(CONTRACT_FIELD_ARGUMENT_TYPE);
    if (argumentType != null && argumentType.isTextual() && !argumentType.asText().isBlank()) {
      return PrimitiveType.fromLabelOptional(argumentType.asText().trim());
    }
    // Contracts stored before argumentType existed still auto-link their targeted asset fields.
    JsonNode type = field.get(CONTRACT_FIELD_TYPE);
    if (type != null && ContractFieldType.TargetedAsset.label.equals(type.asText())) {
      return Optional.of(PrimitiveType.TargetedAsset);
    }
    return Optional.empty();
  }
}
