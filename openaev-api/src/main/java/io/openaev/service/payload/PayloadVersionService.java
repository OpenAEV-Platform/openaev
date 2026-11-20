package io.openaev.service.payload;

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
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.InjectRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private final StepService stepService;
  private final PermissionService permissionService;
  private final UserService userService;

  // -- READ --

  /** Newest first. */
  public List<PayloadVersionOutput> versions(String tenantId, String actionId) {
    List<Payload> versions = payloadService.versionsOf(tenantId, payload(tenantId, actionId));
    Map<String, PayloadRevocation> revocations =
        payloadRevocationService.revocations(tenantId, versions);
    Map<String, InjectorContract> actions =
        injectorContractService.injectorContractsOfPayloads(
            tenantId, versions.stream().map(Payload::getId).toList());
    return versions.stream()
        .sorted(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER.reversed()))
        .map(
            version ->
                output(version, actions.get(version.getId()), revocations.get(version.getId())))
        .toList();
  }

  /** Refused when the version is not another version of the payload, or is revoked. */
  public InjectorContract otherVersionAction(String tenantId, Payload payload, String versionId) {
    refuseUnlessOtherVersion(tenantId, payload, versionId);
    return Optional.ofNullable(
            injectorContractService
                .injectorContractsOfPayloads(tenantId, List.of(versionId))
                .get(versionId))
        .orElseThrow(() -> new ElementNotFoundException(NO_PAYLOAD));
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

  /** The scenario injects and steps running these versions, by scenario id. */
  public Map<String, List<PayloadUsageOutput>> scenarioUsages(
      String tenantId, Collection<Payload> versions) {
    return injectorContractService
        .injectorContractsOfPayloads(tenantId, versions.stream().map(Payload::getId).toList())
        .values()
        .stream()
        .flatMap(action -> usages(tenantId, action).stream())
        .filter(usage -> usage.scenarioId() != null)
        .collect(
            Collectors.groupingBy(
                PayloadUsageOutput::scenarioId, LinkedHashMap::new, Collectors.toList()));
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
    return injectRepository.findAllOutsideSimulationsByTenantIdAndPayloadId(
        tenantId, action.getPayload().getId());
  }

  /** Chained scenario steps running this version. */
  public List<Step> stepsOn(String tenantId, InjectorContract action) {
    return stepService.findScenarioStepTemplatesByContract(tenantId, action.getId());
  }

  private PayloadVersionOutput output(
      Payload version, InjectorContract action, PayloadRevocation revocation) {
    return new PayloadVersionOutput(
        version.getId(),
        action.getId(),
        version.getVersion(),
        version.isLatest(),
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
