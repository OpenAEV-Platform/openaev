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
import io.openaev.service.catalog.CatalogService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.content.PayloadFieldChanges;
import io.openaev.service.scenario.revision.ScenarioRevisionService;
import io.openaev.utils.SemanticVersion;
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
  private final CatalogService catalogService;
  private final PayloadVersionService payloadVersionService;
  private final PayloadRevocationService payloadRevocationService;
  private final InjectService injectService;
  private final StepRepository stepRepository;
  private final HealthCheckUtils healthCheckUtils;
  private final PermissionService permissionService;
  private final UserService userService;
  private final ContentHistoryService contentHistoryService;
  private final ScenarioRevisionService scenarioRevisionService;
  private final ObjectMapper mapper;

  // -- LIST --

  /** Every version of the action's payload, newest first: local payloads are versioned too. */
  @Transactional(readOnly = true)
  public List<PayloadVersionOutput> versions(String actionId) {
    Payload current = payloadOf(actionId);
    PayloadIdentity identity = current.getIdentity();
    Map<String, Payload> installed =
        payloadVersionService.findVersions(identity).stream()
            .collect(Collectors.toMap(Payload::getVersion, p -> p, (x, y) -> x));
    Map<String, CatalogItem> released =
        isManaged(current)
            ? catalogService
                .findVersions(
                    ContentType.PAYLOAD, identity.getRepository(), identity.getExternalId())
                .stream()
                .collect(Collectors.toMap(CatalogItem::getVersion, i -> i, (x, y) -> x))
            : Map.of();
    Set<String> revoked = payloadRevocationService.revokedIds(installed.values());
    Set<String> versions = new HashSet<>(installed.keySet());
    versions.addAll(released.keySet());
    return versions.stream()
        .sorted(SemanticVersion.ORDER.reversed())
        .map(v -> output(installed.get(v), released.get(v), current, revoked, v))
        .toList();
  }

  // A row is an installed version, a catalogue version not installed, or both.
  private PayloadVersionOutput output(
      Payload payload, CatalogItem item, Payload current, Set<String> revoked, String version) {
    String contractId =
        payload == null ? null : injectorContractService.findByPayload(payload).getId();
    return new PayloadVersionOutput(
        payload == null ? null : payload.getId(),
        contractId,
        payload != null ? payload.getExternalId() : item.getExternalId(),
        version,
        payload != null ? payload.getStatus() : catalogStatus(item),
        payload != null && payload.isLatest(),
        payload != null ? revoked.contains(payload.getId()) : "REVOKED".equals(item.getStatus()),
        payload != null && payload.getId().equals(current.getId()),
        payload != null && !payload.isUninstalled(),
        item == null ? null : item.getChangelog(),
        item == null ? null : item.getPublishedAt(),
        contractId == null
            ? 0
            : usagesOf(injectorContractService.injectorContract(contractId)).size(),
        payload == null ? null : payload.getCreatedAt());
  }

  private static Payload.PAYLOAD_STATUS catalogStatus(CatalogItem item) {
    return Arrays.stream(Payload.PAYLOAD_STATUS.values())
        .filter(s -> s.name().equals(item.getStatus()))
        .findFirst()
        .orElse(Payload.PAYLOAD_STATUS.VERIFIED);
  }

  /** Scenario injects, atomic tests and chained steps running the action's payload. */
  @Transactional(readOnly = true)
  public List<PayloadUsageOutput> usages(String actionId) {
    return usagesOf(injectorContractService.injectorContract(actionId));
  }

  private List<PayloadUsageOutput> usagesOf(InjectorContract contract) {
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
    PageRequest page = PageRequest.of(0, 100);
    if (isManaged(payload)) {
      PayloadIdentity identity = payload.getIdentity();
      List<Payload> versions = payloadVersionService.findVersions(identity);
      List<ContentHistoryOutput> rows =
          new ArrayList<>(
              contentHistoryService
                  .findHistory(ContentType.PAYLOAD, payload.getExternalId(), page)
                  .map(
                      history ->
                          history.getAction() == ContentHistory.ACTION.INSTALL
                              ? withRelease(history, identity, versions)
                              : ContentHistoryOutput.of(history))
                  .getContent());
      rows.addAll(repositoryRevocations(identity, payload.getExternalId()));
      rows.sort(
          Comparator.comparing(
              ContentHistoryOutput::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
      return rows;
    }
    List<String> versionIds =
        payloadVersionService.findVersions(payload.getIdentity()).stream()
            .map(Payload::getId)
            .toList();
    return contentHistoryService
        .findHistoryOfVersions(ContentType.PAYLOAD, versionIds, page)
        .map(ContentHistoryOutput::of)
        .getContent();
  }

  // An install row also tells what the release changed since the previous installed version.
  private ContentHistoryOutput withRelease(
      ContentHistory history, PayloadIdentity identity, List<Payload> versions) {
    ObjectNode details =
        history.getDetails() instanceof ObjectNode node
            ? node.deepCopy()
            : mapper.createObjectNode();
    catalogService
        .find(
            ContentType.PAYLOAD,
            identity.getRepository(),
            history.getExternalId(),
            history.getVersion())
        .map(CatalogItem::getChangelog)
        .ifPresent(changelog -> details.put("changelog", changelog));
    versions.stream()
        .filter(version -> version.getVersion().equals(history.getVersion()))
        .findFirst()
        .ifPresent(
            installed ->
                versions.stream()
                    .filter(
                        version ->
                            SemanticVersion.ORDER.compare(
                                    version.getVersion(), installed.getVersion())
                                < 0)
                    .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER))
                    .ifPresent(
                        previous -> {
                          details.put("compared_to", previous.getVersion());
                          details.set(
                              "changes", PayloadFieldChanges.between(mapper, previous, installed));
                        }));
    return ContentHistoryOutput.of(history, details);
  }

  // Revocation lists are shared by every tenant, so their effect is read here, not filed per
  // tenant.
  private List<ContentHistoryOutput> repositoryRevocations(
      PayloadIdentity identity, String externalId) {
    List<ContentHistoryOutput> rows = new ArrayList<>();
    for (ContentRevocation revocation :
        payloadRevocationService.findRepositoryRevocations(identity.getRepository(), externalId)) {
      ObjectNode source =
          mapper
              .createObjectNode()
              .put("source", "repository")
              .put("repository", revocation.getRepository());
      ObjectNode revoked = source.deepCopy();
      if (revocation.getListSerial() != null) {
        revoked.put("list_serial", revocation.getListSerial());
      }
      if (revocation.getReason() != null) {
        revoked.put("reason", revocation.getReason());
      }
      rows.add(
          ContentHistoryOutput.fromRepository(
              "revocation-" + revocation.getId(),
              ContentHistory.ACTION.REVOKE,
              revocation.getVersion(),
              revoked,
              revocation.getCreatedAt()));
      if (revocation.getLiftedAt() != null) {
        rows.add(
            ContentHistoryOutput.fromRepository(
                "lift-" + revocation.getId(),
                ContentHistory.ACTION.LIFT_REVOCATION,
                revocation.getVersion(),
                source,
                revocation.getLiftedAt()));
      }
    }
    return rows;
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
    if (!source.getIdentity().getId().equals(target.getIdentity().getId())) {
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
    scenarioRevisionService.takeIfCatalog(
        scenariosOf(input), ScenarioRevision.Reason.PAYLOAD_VERSION_CHANGE);

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
      contentHistoryService.payloadVersionChanged(source, target, moved.size());
    }
    return new PayloadVersionChangeOutput(moved, refused);
  }

  /**
   * Moves every scenario inject, atomic test and chained step of a local copy to the catalogue
   * version with the same content, when the copy cannot become that version (RFC 0005 §4.5). Done
   * by the platform: no user right applies, an incompatible usage stays where it is.
   */
  public int repointAllUsages(Payload source, Payload target) {
    InjectorContract sourceContract = injectorContractService.findByPayload(source);
    InjectorContract targetContract = injectorContractService.findByPayload(target);
    if (sourceContract == null || targetContract == null) {
      return 0;
    }
    int moved = 0;
    List<Inject> injects = injectService.findTemplateInjectsByPayloadId(source.getId());
    List<Step> steps = scenarioTemplateSteps(sourceContract);
    List<Scenario> scenarios = new ArrayList<>();
    injects.stream().map(Inject::getScenario).filter(Objects::nonNull).forEach(scenarios::add);
    steps.forEach(step -> scenarios.add(step.getWorkflow().getScenario()));
    scenarioRevisionService.takeIfCatalog(
        scenarios, ScenarioRevision.Reason.PAYLOAD_VERSION_CHANGE);
    for (Inject inject : injects) {
      Set<String> after = contentErrors(targetContract, inject);
      after.removeAll(contentErrors(sourceContract, inject));
      if (after.isEmpty()) {
        inject.setInjectorContract(targetContract);
        injectService.save(inject);
        moved++;
      }
    }
    for (Step step : steps) {
      if (repointStep(step, target, targetContract)) {
        moved++;
      }
    }
    if (moved > 0) {
      contentHistoryService.payloadVersionChanged(source, target, moved);
    }
    return moved;
  }

  // Catalogue scenarios keep a revision of their content before their injects move (ADR-011).
  private List<Scenario> scenariosOf(PayloadVersionChangeInput input) {
    List<Scenario> scenarios = new ArrayList<>();
    input
        .injectIdsOrEmpty()
        .forEach(
            id ->
                Optional.ofNullable(injectService.findInjectOrNull(id))
                    .map(Inject::getScenario)
                    .ifPresent(scenarios::add));
    input
        .stepIdsOrEmpty()
        .forEach(
            id ->
                stepRepository
                    .findById(id)
                    .map(step -> step.getWorkflow().getScenario())
                    .ifPresent(scenarios::add));
    return scenarios;
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
      writeStepContract(step, data, targetContract);
      return Optional.empty();
    } catch (Exception e) {
      return Optional.of("Unreadable step data");
    }
  }

  private boolean repointStep(Step step, Payload target, InjectorContract targetContract) {
    try {
      ObjectNode data = (ObjectNode) mapper.readTree(step.getData());
      if (!missingArguments(target, data.path("inject_content")).isEmpty()) {
        return false;
      }
      writeStepContract(step, data, targetContract);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private void writeStepContract(Step step, ObjectNode data, InjectorContract targetContract)
      throws Exception {
    if (data.get(STEP_CONTRACT_FIELD) instanceof ObjectNode contractNode) {
      contractNode.put("injector_contract_id", targetContract.getId());
    } else {
      data.put(STEP_CONTRACT_FIELD, targetContract.getId());
    }
    step.setData(mapper.writeValueAsString(data));
    stepRepository.save(step);
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
