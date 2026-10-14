package io.openaev.service.payload;

import static io.openaev.service.payload.PayloadVersionService.isManaged;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.api.threat_arsenal.ContentHistoryOutput;
import io.openaev.api.threat_arsenal.PayloadUsageOutput;
import io.openaev.api.threat_arsenal.PayloadVersionChangeInput;
import io.openaev.api.threat_arsenal.PayloadVersionChangeOutput;
import io.openaev.api.threat_arsenal.PayloadVersionChangeOutput.Refusal;
import io.openaev.api.threat_arsenal.PayloadVersionOutput;
import io.openaev.database.model.*;
import io.openaev.database.repository.StepRepository;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.content.ContentHistoryService;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves injects and chained steps from one payload version to another (F30), so a fix can be used
 * without waiting for a revocation. Simulations already created keep their version.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PayloadVersionChangeService {

  private static final String STEP_CONTRACT_FIELD = "inject_injector_contract";

  private final InjectorContractService injectorContractService;
  private final PayloadVersionService payloadVersionService;
  private final PayloadRevocationService payloadRevocationService;
  private final InjectService injectService;
  private final StepRepository stepRepository;
  private final HealthCheckUtils healthCheckUtils;
  private final PermissionService permissionService;
  private final UserService userService;
  private final ContentHistoryService contentHistoryService;
  private final ObjectMapper mapper;

  // -- LIST --

  /** Every version of the action's payload, newest first. */
  @Transactional(readOnly = true)
  public List<PayloadVersionOutput> versions(String actionId) {
    Payload current = payloadOf(actionId);
    List<Payload> versions =
        isManaged(current)
            ? payloadVersionService.findAllByExternalId(current.getExternalId()).stream()
                .filter(PayloadVersionService::isManaged)
                .toList()
            : List.of(current);
    Set<String> revoked = payloadRevocationService.revokedIds(versions);
    return versions.stream()
        .sorted(Comparator.comparing(Payload::getCreatedAt).reversed())
        .map(
            p ->
                new PayloadVersionOutput(
                    p.getId(),
                    p.getVersion(),
                    p.getStatus(),
                    p.isLatest(),
                    revoked.contains(p.getId()),
                    p.getId().equals(current.getId()),
                    p.getCreatedAt()))
        .toList();
  }

  /** Scenario injects, atomic tests and chained steps running the action's payload. */
  @Transactional(readOnly = true)
  public List<PayloadUsageOutput> usages(String actionId) {
    InjectorContract contract = injectorContractService.injectorContract(actionId);
    Payload payload = payloadOf(contract);
    List<PayloadUsageOutput> usages = new ArrayList<>();
    for (Inject inject : injectService.findTemplateInjectsByPayloadId(payload.getId())) {
      Scenario scenario = inject.getScenario();
      usages.add(
          new PayloadUsageOutput(
              scenario != null
                  ? PayloadUsageOutput.TYPE_INJECT
                  : PayloadUsageOutput.TYPE_ATOMIC_TESTING,
              inject.getId(),
              inject.getTitle(),
              scenario != null ? scenario.getId() : null,
              scenario != null ? scenario.getName() : null));
    }
    for (Step step : scenarioTemplateSteps(contract)) {
      Scenario scenario = step.getWorkflow().getScenario();
      usages.add(
          new PayloadUsageOutput(
              PayloadUsageOutput.TYPE_STEP,
              step.getId(),
              stepTitle(step),
              scenario.getId(),
              scenario.getName()));
    }
    return usages;
  }

  /** History of the action's payload across its versions, newest first (F7). */
  @Transactional(readOnly = true)
  public List<ContentHistoryOutput> history(String actionId) {
    Payload payload = payloadOf(actionId);
    if (!isManaged(payload)) {
      return List.of();
    }
    return contentHistoryService
        .findHistory(ContentType.PAYLOAD, payload.getExternalId(), PageRequest.of(0, 100))
        .map(ContentHistoryOutput::of)
        .getContent();
  }

  // -- UPDATE --

  /**
   * Repoints the given injects and steps to the target version. Each one is checked on its own: a
   * missing right, or arguments the target version no longer accepts, refuses that one only.
   */
  public PayloadVersionChangeOutput changeVersion(
      String actionId, PayloadVersionChangeInput input) {
    InjectorContract sourceContract = injectorContractService.injectorContract(actionId);
    Payload source = payloadOf(sourceContract);
    Payload target = payloadVersionService.findById(input.targetPayloadId());
    if (!isManaged(source)
        || !isManaged(target)
        || !source.getExternalId().equals(target.getExternalId())) {
      throw new BadRequestException("The target is not another version of this payload");
    }
    if (source.getId().equals(target.getId())) {
      throw new BadRequestException("The target is the current version");
    }
    if (payloadRevocationService.isRevoked(target)) {
      throw new BadRequestException("The target version is revoked");
    }
    InjectorContract targetContract = injectorContractService.findByPayload(target);
    User user = userService.currentUser();

    List<String> moved = new ArrayList<>();
    List<Refusal> refused = new ArrayList<>();
    for (String injectId : input.injectIdsOrEmpty()) {
      Optional<String> refusal = moveInject(injectId, source, sourceContract, targetContract, user);
      refusal.ifPresentOrElse(
          reason -> refused.add(new Refusal(injectId, reason)), () -> moved.add(injectId));
    }
    for (String stepId : input.stepIdsOrEmpty()) {
      Optional<String> refusal = moveStep(stepId, sourceContract, target, targetContract, user);
      refusal.ifPresentOrElse(
          reason -> refused.add(new Refusal(stepId, reason)), () -> moved.add(stepId));
    }
    if (!moved.isEmpty()) {
      contentHistoryService.payloadVersionChanged(source, target);
    }
    return new PayloadVersionChangeOutput(moved, refused);
  }

  private Optional<String> moveInject(
      String injectId,
      Payload source,
      InjectorContract sourceContract,
      InjectorContract targetContract,
      User user) {
    Inject inject = injectService.findInjectOrNull(injectId);
    if (inject == null
        || inject.getExercise() != null
        || inject.getPayload().map(p -> !p.getId().equals(source.getId())).orElse(true)) {
      return Optional.of("Does not use this payload version");
    }
    Scenario scenario = inject.getScenario();
    boolean allowed =
        scenario != null
            ? permissionService.hasPermission(
                user, Optional.empty(), scenario.getId(), ResourceType.SCENARIO, Action.WRITE)
            : permissionService.hasPermission(
                user, Optional.empty(), inject.getId(), ResourceType.ATOMIC_TESTING, Action.WRITE);
    if (!allowed) {
      return Optional.of("Missing right to update it");
    }
    // Only what the target version adds is refused: an inject already incomplete stays as it is.
    Set<String> before = contentErrors(sourceContract, inject);
    Set<String> after = contentErrors(targetContract, inject);
    after.removeAll(before);
    if (!after.isEmpty()) {
      return Optional.of("Not compatible with this version: " + String.join(", ", after));
    }
    inject.setInjectorContract(targetContract);
    injectService.save(inject);
    return Optional.empty();
  }

  private Optional<String> moveStep(
      String stepId,
      InjectorContract sourceContract,
      Payload target,
      InjectorContract targetContract,
      User user) {
    Optional<Step> stepOpt =
        scenarioTemplateSteps(sourceContract).stream()
            .filter(s -> s.getId().equals(stepId))
            .findFirst();
    if (stepOpt.isEmpty()) {
      return Optional.of("Does not use this payload version");
    }
    Step step = stepOpt.get();
    Scenario scenario = step.getWorkflow().getScenario();
    if (!permissionService.hasPermission(
        user, Optional.empty(), scenario.getId(), ResourceType.SCENARIO, Action.WRITE)) {
      return Optional.of("Missing right to update it");
    }
    try {
      ObjectNode data = (ObjectNode) mapper.readTree(step.getData());
      List<String> missing = missingArguments(target, data.path("inject_content"));
      if (!missing.isEmpty()) {
        return Optional.of("Not compatible with this version: " + String.join(", ", missing));
      }
      if (data.get(STEP_CONTRACT_FIELD) instanceof ObjectNode contractNode) {
        contractNode.put("injector_contract_id", targetContract.getId());
      } else {
        data.put(STEP_CONTRACT_FIELD, targetContract.getId());
      }
      step.setData(mapper.writeValueAsString(data));
      stepRepository.save(step);
      return Optional.empty();
    } catch (Exception e) {
      return Optional.of("Unreadable step data");
    }
  }

  private List<Step> scenarioTemplateSteps(InjectorContract contract) {
    return stepRepository
        .findTemplateStepsByInjectorContractIds(
            List.of(contract.getId()), contract.getTenant().getId())
        .stream()
        .filter(s -> s.getWorkflow() != null && s.getWorkflow().getScenario() != null)
        .toList();
  }

  private Set<String> contentErrors(InjectorContract contract, Inject inject) {
    return healthCheckUtils
        .runContentChecks(
            contract,
            inject.getContent(),
            inject.isAllTeams(),
            inject.getTeams().stream().map(Team::getId).toList(),
            inject.getAssets().stream().map(Asset::getId).toList(),
            inject.getAssetGroups().stream().map(AssetGroup::getId).toList())
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

  private String stepTitle(Step step) {
    try {
      return mapper.readTree(step.getData()).path("inject_title").asText(null);
    } catch (Exception e) {
      return null;
    }
  }

  private Payload payloadOf(String actionId) {
    return payloadOf(injectorContractService.injectorContract(actionId));
  }

  private static Payload payloadOf(InjectorContract contract) {
    if (contract.getPayload() == null) {
      throw new BadRequestException("This action has no payload");
    }
    return contract.getPayload();
  }
}
