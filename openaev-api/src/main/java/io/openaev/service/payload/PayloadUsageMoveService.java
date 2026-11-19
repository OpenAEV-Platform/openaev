package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.StepService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves scenario injects, atomic testings and chained steps from one version of a payload to
 * another. Each one is checked on its own: a missing right, or arguments the target version no
 * longer accepts, leaves that one where it is. Simulations already created keep their version.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadUsageMoveService {

  private static final String NOT_ANOTHER_VERSION =
      "The target is not another version of this payload";
  private static final String TARGET_REVOKED = "The target version is revoked";
  private static final String NOT_ON_THIS_VERSION = "Does not use this payload version";
  private static final String MISSING_RIGHT = "Missing right to update it";
  private static final String NOT_COMPATIBLE = "Not compatible with this version: ";
  private static final String UNREADABLE_STEP = "Unreadable step data";

  private static final String STEP_CONTRACT_FIELD = "inject_injector_contract";
  private static final String STEP_CONTRACT_ID_FIELD = "injector_contract_id";
  private static final String STEP_CONTENT_FIELD = "inject_content";

  private final InjectorContractService injectorContractService;
  private final PayloadService payloadService;
  private final PayloadRevocationService payloadRevocationService;
  private final InjectService injectService;
  private final StepService stepService;
  private final HealthCheckUtils healthCheckUtils;
  private final PermissionService permissionService;
  private final UserService userService;
  private final ObjectMapper mapper;

  public record Refusal(String usageId, String reason) {}

  public record Moves(List<String> moved, List<Refusal> refused) {}

  // -- UPDATE --

  public Moves move(
      TxCtx ctx,
      String tenantId,
      String actionId,
      String targetPayloadId,
      List<String> injectIds,
      List<String> stepIds) {
    InjectorContract sourceAction =
        injectorContractService.injectorContractInTenant(actionId, tenantId);
    Payload source = sourceAction.getPayload();
    if (source == null) {
      throw new ElementNotFoundException(PayloadVersionService.NO_PAYLOAD);
    }
    Payload target = otherVersion(tenantId, source, targetPayloadId);
    InjectorContract targetAction =
        injectorContractService
            .injectorContractsOfPayloads(tenantId, List.of(target.getId()))
            .get(target.getId());
    User user = userService.currentUser();

    List<String> moved = new ArrayList<>();
    List<Refusal> refused = new ArrayList<>();
    for (String injectId : injectIds) {
      moveInject(ctx, user, injectId, source, sourceAction, targetAction)
          .ifPresentOrElse(
              reason -> refused.add(new Refusal(injectId, reason)), () -> moved.add(injectId));
    }
    List<Step> steps =
        stepService.findScenarioStepTemplatesByContract(tenantId, sourceAction.getId());
    for (String stepId : stepIds) {
      moveStep(ctx, user, stepId, steps, target, targetAction)
          .ifPresentOrElse(
              reason -> refused.add(new Refusal(stepId, reason)), () -> moved.add(stepId));
    }
    return new Moves(moved, refused);
  }

  private Payload otherVersion(String tenantId, Payload source, String targetPayloadId) {
    Payload target =
        payloadService.versionsOf(tenantId, source).stream()
            .filter(version -> version.getId().equals(targetPayloadId))
            .findFirst()
            .orElseThrow(() -> new BadRequestException(NOT_ANOTHER_VERSION));
    if (target.getId().equals(source.getId())) {
      throw new BadRequestException(NOT_ANOTHER_VERSION);
    }
    if (payloadRevocationService
        .revocations(tenantId, List.of(target))
        .containsKey(target.getId())) {
      throw new BadRequestException(TARGET_REVOKED);
    }
    return target;
  }

  private Optional<String> moveInject(
      TxCtx ctx,
      User user,
      String injectId,
      Payload source,
      InjectorContract sourceAction,
      InjectorContract targetAction) {
    Optional<Inject> found =
        injectService
            .findInScope(ctx, injectId)
            .filter(inject -> inject.getExercise() == null)
            .filter(
                inject ->
                    inject.getPayload().map(p -> p.getId().equals(source.getId())).orElse(false));
    if (found.isEmpty()) {
      return Optional.of(NOT_ON_THIS_VERSION);
    }
    Inject inject = found.get();
    Scenario scenario = inject.getScenario();
    boolean allowed =
        scenario != null
            ? permissionService.hasPermissionInTenant(
                ctx, user, scenario.getId(), ResourceType.SCENARIO, Action.WRITE)
            : permissionService.hasPermissionInTenant(
                ctx, user, inject.getId(), ResourceType.ATOMIC_TESTING, Action.WRITE);
    if (!allowed) {
      return Optional.of(MISSING_RIGHT);
    }
    // Only what the target version adds is refused: an inject already incomplete stays as it is.
    Set<String> added = contentErrors(targetAction, inject);
    added.removeAll(contentErrors(sourceAction, inject));
    if (!added.isEmpty()) {
      return Optional.of(NOT_COMPATIBLE + String.join(", ", added));
    }
    inject.setInjectorContract(targetAction);
    return Optional.empty();
  }

  private Optional<String> moveStep(
      TxCtx ctx,
      User user,
      String stepId,
      List<Step> steps,
      Payload target,
      InjectorContract targetAction) {
    Optional<Step> found = steps.stream().filter(step -> step.getId().equals(stepId)).findFirst();
    if (found.isEmpty()) {
      return Optional.of(NOT_ON_THIS_VERSION);
    }
    Step step = found.get();
    Scenario scenario = step.getWorkflow().getScenario();
    if (!permissionService.hasPermissionInTenant(
        ctx, user, scenario.getId(), ResourceType.SCENARIO, Action.WRITE)) {
      return Optional.of(MISSING_RIGHT);
    }
    try {
      ObjectNode data = (ObjectNode) mapper.readTree(step.getData());
      List<String> missing = missingArguments(target, data.path(STEP_CONTENT_FIELD));
      if (!missing.isEmpty()) {
        return Optional.of(NOT_COMPATIBLE + String.join(", ", missing));
      }
      if (data.get(STEP_CONTRACT_FIELD) instanceof ObjectNode contract) {
        contract.put(STEP_CONTRACT_ID_FIELD, targetAction.getId());
      } else {
        data.put(STEP_CONTRACT_FIELD, targetAction.getId());
      }
      step.setData(mapper.writeValueAsString(data));
      stepService.saveStep(step);
      return Optional.empty();
    } catch (Exception e) {
      return Optional.of(UNREADABLE_STEP);
    }
  }

  private Set<String> contentErrors(InjectorContract action, Inject inject) {
    return healthCheckUtils
        .runContentChecks(
            action,
            inject.getContent(),
            inject.isAllTeams(),
            inject.getTeams().stream().map(Team::getId).toList(),
            inject.getAssets().stream().map(Asset::getId).toList(),
            inject.getAssetGroups().stream().map(AssetGroup::getId).toList(),
            inject.getSecretReferences().stream().map(SecretReference::getId).toList())
        .stream()
        .filter(check -> check.getStatus() == HealthCheck.Status.ERROR)
        .map(check -> check.getType() + " " + check.getDetail())
        .collect(Collectors.toCollection(HashSet::new));
  }

  /** Target arguments without a value in the step content and without a default. */
  private static List<String> missingArguments(Payload target, JsonNode content) {
    return target.getArguments().stream()
        .filter(arg -> arg.getDefaultValue() == null || arg.getDefaultValue().isBlank())
        .map(PayloadArgument::getKey)
        .filter(key -> !content.hasNonNull(key) || content.get(key).asText().isBlank())
        .toList();
  }
}
