package io.openaev.service.chaining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.rest.injector_contract.InjectorContractService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Classifies chaining steps by targeting axis from their serialized inject data.
 *
 * <p>The chaining engine distinguishes two step families:
 *
 * <ul>
 *   <li><b>Asset/IP-centric</b> (technical): payload injects, nmap, nuclei, ... They consume the
 *       scope's assets, asset groups and network targets.
 *   <li><b>Audience-centric</b> (tabletop): email, SMS, challenge, ... They consume teams and
 *       players.
 * </ul>
 *
 * <p>This classification drives both the engine (scope fan-out and audience resolution in {@code
 * InjectExecutionStep}) and the payload-type-aware launch validation ({@code HealthCheckUtils}), so
 * it lives in a single service to keep the two in sync.
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class StepTargetingService {

  /**
   * Contract field types that mean "this inject targets assets/IPs": the audience-picking widgets
   * on the inject form (asset, asset group, network target selector). Mirrors the frontend {@code
   * INJECTOR_HIDDEN_TYPES} used by the chaining Configure-action drawer.
   */
  private static final Set<String> ASSET_TARGET_FIELD_TYPES =
      Set.of(
          InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE_ASSET,
          InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP,
          "targeted-asset");

  /** Contract field key that drives network (IP/manual) targeting on external injectors. */
  private static final String TARGET_SELECTOR_FIELD_KEY = "target_selector";

  private final InjectorContractService injectorContractService;
  private final ConditionService conditionService;

  /**
   * Returns {@code true} if the step data contains a non-null payload inside its injector contract
   * ({@code inject_injector_contract.injector_contract_payload}).
   */
  public boolean hasPayload(Step step) {
    JsonObject contract = contractFromStepData(step);
    if (contract == null) {
      return false;
    }
    JsonElement payloadElement = contract.get("injector_contract_payload");
    if (payloadElement == null || payloadElement.isJsonNull() || !payloadElement.isJsonObject()) {
      return false;
    }
    JsonElement payloadType = payloadElement.getAsJsonObject().get("payload_type");
    return payloadType != null && !payloadType.isJsonNull();
  }

  /**
   * Classifies a step as asset/IP-centric (nmap, nuclei, payload injects, ...) versus
   * audience-centric (email, SMS, ...) from its serialized injector contract.
   *
   * <p>Payload injects always run on an endpoint, so they are asset-centric by definition.
   * Otherwise the contract fields are inspected the same way the frontend does: first from the
   * contract content snapshot baked into the step data, then from the live contract as a fallback.
   *
   * <p>When the contract cannot be resolved at all we fall back to {@code true} (asset-centric) to
   * preserve the historical scope-expansion behavior for existing chains.
   */
  public boolean isAssetCentric(Step step) {
    if (hasPayload(step)) {
      return true;
    }
    JsonObject contract = contractFromStepData(step);
    if (contract != null) {
      JsonElement content = contract.get("injector_contract_content");
      if (content != null && content.isJsonPrimitive()) {
        try {
          JsonElement parsed = JsonParser.parseString(content.getAsString());
          if (parsed.isJsonObject()) {
            return fieldsSupportAssetTargeting(
                parsed.getAsJsonObject().get(InjectorContract.CONTRACT_CONTENT_FIELDS));
          }
        } catch (Exception e) {
          log.warn(
              "Failed to parse contract content snapshot for step {}; falling back to live contract",
              step.getId());
        }
      }
    }
    String contractId = contractIdFromStepData(step);
    if (contractId == null) {
      return true;
    }
    try {
      return supportsAssetTargeting(injectorContractService.injectorContract(contractId));
    } catch (Exception e) {
      log.warn(
          "Failed to resolve injector contract {} while classifying step {} targeting; assuming asset-centric",
          contractId,
          step.getId());
      return true;
    }
  }

  /**
   * Returns {@code true} when the injector contract exposes an asset / asset-group / network-target
   * field, i.e. the inject targets assets or raw IPs rather than an audience of teams.
   */
  public boolean supportsAssetTargeting(InjectorContract injectorContract) {
    if (injectorContract == null || injectorContract.getConvertedContent() == null) {
      return false;
    }
    com.fasterxml.jackson.databind.JsonNode fields =
        injectorContract.getConvertedContent().get(InjectorContract.CONTRACT_CONTENT_FIELDS);
    if (fields == null || !fields.isArray()) {
      return false;
    }
    for (com.fasterxml.jackson.databind.JsonNode field : fields) {
      String type = field.path(InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE).asText("");
      String key = field.path(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY).asText("");
      if (ASSET_TARGET_FIELD_TYPES.contains(type) || TARGET_SELECTOR_FIELD_KEY.equals(key)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns {@code true} when the step data carries an explicitly configured audience: all-teams
   * mode or teams selected in the Configure-action drawer. Such steps do not need audience entries
   * in the workflow scope.
   */
  public boolean hasExplicitAudience(Step step) {
    JsonObject data = parseStepData(step);
    if (data == null) {
      return false;
    }
    JsonElement allTeams = data.get("inject_all_teams");
    if (allTeams != null && allTeams.isJsonPrimitive() && allTeams.getAsBoolean()) {
      return true;
    }
    return hasNonEmptyArray(data, "inject_teams");
  }

  /**
   * Returns {@code true} when the step data carries explicitly configured technical targets:
   * assets, asset groups, or manual network targets in the inject content. Such steps do not need
   * technical entries in the workflow scope.
   */
  public boolean hasExplicitTechnicalTargets(Step step) {
    JsonObject data = parseStepData(step);
    if (data == null) {
      return false;
    }
    if (hasNonEmptyArray(data, "inject_assets") || hasNonEmptyArray(data, "inject_asset_groups")) {
      return true;
    }
    JsonElement content = data.get("inject_content");
    if (content == null || !content.isJsonObject()) {
      return false;
    }
    JsonObject contentObject = content.getAsJsonObject();
    JsonElement selector = contentObject.get(TARGET_SELECTOR_FIELD_KEY);
    JsonElement targets = contentObject.get("targets");
    return selector != null
        && selector.isJsonPrimitive()
        && "manual".equals(selector.getAsString())
        && targets != null
        && targets.isJsonPrimitive()
        && !targets.getAsString().isBlank();
  }

  /**
   * Returns {@code true} when the step has at least one MAPPER condition, i.e. its inject content
   * (including potential targets or recipients) is fed dynamically from upstream step outputs.
   */
  public boolean hasMapperConditions(Step step) {
    if (step.getId() == null) {
      return false;
    }
    List<Condition> conditions = conditionService.findAllConditionsByStepId(step.getId());
    return conditions != null
        && conditions.stream()
            .anyMatch(condition -> ConditionType.MAPPER.equals(condition.getType()));
  }

  /** Reads {@code inject_injector_contract.injector_contract_id} from serialized step data. */
  private String contractIdFromStepData(Step step) {
    JsonObject contract = contractFromStepData(step);
    if (contract == null) {
      return null;
    }
    JsonElement idElement = contract.get("injector_contract_id");
    return (idElement == null || idElement.isJsonNull()) ? null : idElement.getAsString();
  }

  /** Reads the serialized {@code inject_injector_contract} object from step data. */
  private JsonObject contractFromStepData(Step step) {
    JsonObject data = parseStepData(step);
    if (data == null) {
      return null;
    }
    JsonElement contractElement = data.get("inject_injector_contract");
    if (contractElement == null
        || contractElement.isJsonNull()
        || !contractElement.isJsonObject()) {
      return null;
    }
    return contractElement.getAsJsonObject();
  }

  private JsonObject parseStepData(Step step) {
    if (step.getData() == null) {
      return null;
    }
    try {
      JsonElement parsed = JsonParser.parseString(step.getData());
      return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    } catch (Exception e) {
      log.warn("Failed to parse step data for step ID: {}", step.getId());
      return null;
    }
  }

  private boolean hasNonEmptyArray(JsonObject data, String key) {
    JsonElement element = data.get(key);
    return element != null && element.isJsonArray() && !element.getAsJsonArray().isEmpty();
  }

  /** Gson variant of the contract-fields inspection, applied to the step-data content snapshot. */
  private boolean fieldsSupportAssetTargeting(JsonElement fields) {
    if (fields == null || !fields.isJsonArray()) {
      return false;
    }
    JsonArray fieldArray = fields.getAsJsonArray();
    for (JsonElement fieldElement : fieldArray) {
      if (!fieldElement.isJsonObject()) {
        continue;
      }
      JsonObject field = fieldElement.getAsJsonObject();
      String type = asTextOrEmpty(field.get(InjectorContract.CONTRACT_ELEMENT_CONTENT_TYPE));
      String key = asTextOrEmpty(field.get(InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY));
      if (ASSET_TARGET_FIELD_TYPES.contains(type) || TARGET_SELECTOR_FIELD_KEY.equals(key)) {
        return true;
      }
    }
    return false;
  }

  private String asTextOrEmpty(JsonElement element) {
    return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
  }
}
