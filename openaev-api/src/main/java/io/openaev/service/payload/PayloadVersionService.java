package io.openaev.service.payload;

import io.openaev.api.threat_arsenal.dto.PayloadUsageCountOutput;
import io.openaev.api.threat_arsenal.dto.PayloadUsageOutput;
import io.openaev.api.threat_arsenal.dto.PayloadUsagesOutput;
import io.openaev.api.threat_arsenal.dto.PayloadVersionOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.User;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.raw.RawOtherVersionAction;
import io.openaev.database.raw.RawScenarioUsageCount;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The versions of a Threat Arsenal action's payload installed in its tenant, and their usages. */
@Service
@RequiredArgsConstructor
public class PayloadVersionService {

  public static final String NO_PAYLOAD = "This action has no payload";
  public static final String NOT_ANOTHER_VERSION = "This is not another version of the payload";
  public static final String VERSION_REVOKED = "This version is revoked";

  private final InjectorContractService injectorContractService;
  private final PayloadService payloadService;
  private final PayloadRevocationService payloadRevocationService;
  private final InjectRepository injectRepository;
  private final PayloadIdentityRepository payloadIdentityRepository;
  private final StepService stepService;
  private final PermissionService permissionService;
  private final UserService userService;
  private final PayloadChangeKind payloadChangeKind;

  // -- READ --

  /** Newest first. */
  public List<PayloadVersionOutput> versions(String tenantId, String actionId) {
    List<Payload> versions = versionsOf(tenantId, actionId);
    Map<String, PayloadRevocation> revocations =
        payloadRevocationService.revocations(tenantId, versions);
    Map<String, InjectorContract> actions = versionActions(tenantId, versions);
    return versions.stream()
        .sorted(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER.reversed()))
        .map(
            version ->
                output(version, actions.get(version.getId()), revocations.get(version.getId())))
        .toList();
  }

  /** What this version changed since the one just below it; empty for the first version. */
  public Optional<PayloadChange> changeFromPrevious(String tenantId, String actionId) {
    Payload version = payload(tenantId, actionId);
    return Optional.ofNullable(
            changesFromPrevious(tenantId, version, Set.of(version.getVersion()))
                .get(version.getVersion()))
        .map(PayloadVersionChange::change);
  }

  /** By version, what each of these versions changed since the one just below it. */
  public Map<String, PayloadVersionChange> changesFromPrevious(
      String tenantId, String actionId, Collection<String> versions) {
    if (versions.isEmpty()) {
      return Map.of();
    }
    return changesFromPrevious(tenantId, payload(tenantId, actionId), Set.copyOf(versions));
  }

  private Map<String, PayloadVersionChange> changesFromPrevious(
      String tenantId, Payload payload, Set<String> versions) {
    List<Payload> ordered =
        payloadService.versionsOf(tenantId, payload).stream()
            .sorted(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER))
            .toList();
    Map<String, PayloadVersionChange> changes = new HashMap<>();
    for (int i = 1; i < ordered.size(); i++) {
      Payload previous = ordered.get(i - 1);
      Payload version = ordered.get(i);
      if (versions.contains(version.getVersion())) {
        changes.put(
            version.getVersion(),
            new PayloadVersionChange(
                previous.getVersion(),
                payloadChangeKind.since(previous, List.of(version)).getFirst()));
      }
    }
    return changes;
  }

  /** Refused when the version is not another version of the payload, or is revoked. */
  public InjectorContract otherVersionAction(String tenantId, Payload payload, String versionId) {
    refuseUnlessOtherVersion(tenantId, payload, versionId);
    return versionAction(tenantId, versionId);
  }

  /** Same rule as {@link #otherVersionAction}, from the action of the version. */
  public InjectorContract otherVersionActionById(
      String tenantId, Payload payload, String actionId) {
    InjectorContract action = payloadAction(tenantId, actionId);
    refuseUnlessOtherVersion(tenantId, payload, action.getPayload().getId());
    return action;
  }

  /** The payload version the action runs. */
  public Payload payload(String tenantId, String actionId) {
    return payloadAction(tenantId, actionId).getPayload();
  }

  /** The action the Threat Arsenal lists for a payload version: the one of its latest version. */
  public InjectorContract listedAction(String tenantId, String payloadId) {
    return versionAction(
        tenantId, payloadService.payload(tenantId, payloadId).listedVersion().getId());
  }

  public InjectorContract versionAction(String tenantId, String payloadId) {
    return Optional.ofNullable(
            injectorContractService
                .injectorContractsOfPayloads(tenantId, List.of(payloadId))
                .get(payloadId))
        .orElseThrow(() -> new ElementNotFoundException(NO_PAYLOAD));
  }

  /** Only what the user can read is listed; the others are counted. */
  public PayloadUsagesOutput usages(String tenantId, String actionId) {
    List<PayloadUsageOutput> usages = usages(tenantId, payloadAction(tenantId, actionId));
    User user = userService.currentUser();
    Map<String, Boolean> readable = new HashMap<>();
    List<PayloadUsageOutput> visible =
        usages.stream()
            .filter(
                usage ->
                    readable.computeIfAbsent(
                        usage.readResourceType() + ":" + usage.readResourceId(),
                        key ->
                            permissionService.hasPermissionInTenant(
                                TxCtx.forTenant(tenantId),
                                user,
                                usage.readResourceId(),
                                usage.readResourceType(),
                                Action.READ)))
            .toList();
    return new PayloadUsagesOutput(visible, usages.size() - visible.size());
  }

  /**
   * How many usages each version of the action's payload has, by payload id; unused ones left out.
   */
  public List<PayloadUsageCountOutput> usageCounts(String tenantId, String actionId) {
    Map<String, String> versionOfAction =
        versionActions(tenantId, versionsOf(tenantId, actionId)).values().stream()
            .collect(
                Collectors.toMap(InjectorContract::getId, action -> action.getPayload().getId()));
    return injectRepository
        .countUsagesByTenantIdAndActionIds(tenantId, versionOfAction.keySet())
        .stream()
        .map(
            count ->
                new PayloadUsageCountOutput(
                    versionOfAction.get(count.getActionId()), count.getUsageCount()))
        .toList();
  }

  /** How many scenario injects and steps run these versions, by scenario. */
  public List<RawScenarioUsageCount> scenarioUsages(String tenantId, Collection<Payload> versions) {
    Set<String> actionIds =
        versionActions(tenantId, versions).values().stream()
            .map(InjectorContract::getId)
            .collect(Collectors.toSet());
    return actionIds.isEmpty()
        ? List.of()
        : injectRepository.countScenarioUsagesByTenantIdAndActionIds(tenantId, actionIds);
  }

  private List<Payload> versionsOf(String tenantId, String actionId) {
    return payloadService.versionsOf(tenantId, payload(tenantId, actionId));
  }

  private Map<String, InjectorContract> versionActions(
      String tenantId, Collection<Payload> versions) {
    return injectorContractService.injectorContractsOfPayloads(
        tenantId, versions.stream().map(Payload::getId).toList());
  }

  private List<PayloadUsageOutput> usages(String tenantId, InjectorContract action) {
    Stream<PayloadUsageOutput> injects =
        injectsOn(tenantId, action).stream().map(PayloadUsageOutput::of);
    Stream<PayloadUsageOutput> steps =
        stepsOn(tenantId, action).stream()
            .map(step -> PayloadUsageOutput.of(step, stepService.injectTitle(step)));
    return Stream.concat(injects, steps).toList();
  }

  /** Scenario injects and atomic testings running this version: simulations keep theirs. */
  public List<Inject> injectsOn(String tenantId, InjectorContract action) {
    return injectRepository.findAllOutsideSimulationsByTenantIdAndInjectorContractId(
        tenantId, action.getId());
  }

  /** Chained scenario steps running this version. */
  public List<Step> stepsOn(String tenantId, InjectorContract action) {
    return stepService.findScenarioStepTemplatesByContract(tenantId, action.getId());
  }

  /**
   * Of these actions, the ones whose card says injects still run an older version, with how many.
   */
  public Map<String, Long> olderVersionUsages(String tenantId, List<String> actionIds) {
    if (actionIds.isEmpty()) {
      return Map.of();
    }
    Map<String, List<String>> cardsOfOtherVersion =
        payloadIdentityRepository.findOtherVersionActionsByTenantId(tenantId, actionIds).stream()
            .collect(
                Collectors.groupingBy(
                    RawOtherVersionAction::getOtherVersionActionId,
                    Collectors.mapping(RawOtherVersionAction::getActionId, Collectors.toList())));
    if (cardsOfOtherVersion.isEmpty()) {
      return Map.of();
    }
    return injectRepository
        .countUsagesByTenantIdAndActionIds(tenantId, cardsOfOtherVersion.keySet())
        .stream()
        .flatMap(
            count ->
                cardsOfOtherVersion.get(count.getActionId()).stream()
                    .map(card -> Map.entry(card, count.getUsageCount())))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Long::sum));
  }

  // -- UPDATE --

  /** Kept until a newer version arrives: the card shows no banner meanwhile. */
  public void keepOlderVersions(String tenantId, String actionId, boolean kept) {
    Payload version = payloadAction(tenantId, actionId).getPayload();
    PayloadIdentity identity = version.getIdentity();
    identity.setOlderVersionsKeptFor(kept ? version.getId() : null);
    payloadIdentityRepository.save(identity);
  }

  private PayloadVersionOutput output(
      Payload version, InjectorContract action, PayloadRevocation revocation) {
    return new PayloadVersionOutput(
        version.getId(),
        action.getId(),
        version.getVersion(),
        version.isLatest(),
        version.getId().equals(version.getIdentity().getOlderVersionsKeptFor()),
        revocation != null,
        revocation != null && revocation.isByCatalogue(),
        revocation != null ? revocation.getReason() : null,
        revocation != null ? revocation.getCreatedAt() : null,
        Optional.ofNullable(revocation)
            .map(PayloadRevocation::getUser)
            .map(User::getNameOrEmail)
            .orElse(null));
  }

  private void refuseUnlessOtherVersion(String tenantId, Payload payload, String versionId) {
    boolean otherVersion =
        !payload.getId().equals(versionId)
            && payloadService.versionsOf(tenantId, payload).stream()
                .anyMatch(version -> version.getId().equals(versionId));
    if (!otherVersion) {
      throw new BadRequestException(NOT_ANOTHER_VERSION);
    }
    if (payloadRevocationService.isRevoked(tenantId, versionId)) {
      throw new BadRequestException(VERSION_REVOKED);
    }
  }

  public InjectorContract payloadAction(String tenantId, String actionId) {
    InjectorContract action = injectorContractService.injectorContractInTenant(actionId, tenantId);
    if (action.getPayload() == null) {
      throw new ElementNotFoundException(NO_PAYLOAD);
    }
    return action;
  }
}
