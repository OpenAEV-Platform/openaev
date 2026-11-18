package io.openaev.service.payload;

import io.openaev.api.threat_arsenal.dto.PayloadUsageOutput;
import io.openaev.api.threat_arsenal.dto.PayloadUsagesOutput;
import io.openaev.api.threat_arsenal.dto.PayloadVersionOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.User;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.chaining.StepService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The versions of a Threat Arsenal action's payload installed in its tenant, and their usages. */
@Service
@RequiredArgsConstructor
public class PayloadVersionService {

  public static final String NO_PAYLOAD = "This action has no payload";

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
    List<Payload> versions =
        payloadService.versionsOf(tenantId, payloadAction(tenantId, actionId).getPayload());
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

  /** Scenario injects, atomic testings and chained scenario steps running this version. */
  private List<PayloadUsageOutput> usages(String tenantId, InjectorContract action) {
    Stream<PayloadUsageOutput> injects =
        injectRepository
            .findAllOutsideSimulationsByTenantIdAndPayloadId(tenantId, action.getPayload().getId())
            .stream()
            .map(PayloadUsageOutput::of);
    Stream<PayloadUsageOutput> steps =
        stepService.findScenarioStepTemplatesByContract(tenantId, action.getId()).stream()
            .map(step -> PayloadUsageOutput.of(step, stepService.injectTitle(step)));
    return Stream.concat(injects, steps).toList();
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

  private InjectorContract payloadAction(String tenantId, String actionId) {
    InjectorContract action = injectorContractService.injectorContractInTenant(actionId, tenantId);
    if (action.getPayload() == null) {
      throw new ElementNotFoundException(NO_PAYLOAD);
    }
    return action;
  }
}
