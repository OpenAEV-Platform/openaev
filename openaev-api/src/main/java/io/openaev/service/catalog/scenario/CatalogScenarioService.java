package io.openaev.service.catalog.scenario;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.scenario.ScenarioIdentity;
import io.openaev.database.repository.ScenarioIdentityRepository;
import io.openaev.importer.ImportResult;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.ImportService;
import io.openaev.service.catalog.event.CatalogChangePublisher;
import io.openaev.service.catalog.payload.CatalogPayloadService;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.catalog.scenario.targeting.CatalogScenarioTargets;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import io.openaev.service.organization.OrganizationService;
import io.openaev.service.scenario.ScenarioService;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue scenarios in a tenant: installed once, running only the payload versions their release
 * names. Uninstalling one is deleting it.
 */
@Service
@RequiredArgsConstructor
public class CatalogScenarioService {

  public static final String ALREADY_INSTALLED = "This scenario is already installed";
  public static final String OTHER_PAYLOAD_REFUSED =
      "The scenario runs a payload its release does not name";

  private final ScenarioService scenarioService;
  private final ScenarioIdentityRepository scenarioIdentityRepository;
  private final InjectService injectService;
  private final ImportService importService;
  private final CatalogPayloadService catalogPayloadService;
  private final CatalogScenarioTargets catalogScenarioTargets;
  private final OrganizationService organizationService;
  private final CatalogChangePublisher changePublisher;

  // -- CREATE --

  /**
   * Installs the payload versions the release names, then imports the package, whose payloads link
   * to these versions by external ID and content, and adds the chosen targets.
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario install(
      TxCtx ctx,
      String tenantId,
      InstallableScenarioVersion released,
      byte[] archive,
      Map<InstallablePayloadVersion, byte[]> payloadFiles,
      TargetChoice targets,
      CatalogHistory.Mode mode)
      throws Exception {
    ScenarioIdentity identity = uninstalledIdentity(tenantId, released);
    Set<String> payloadIds = installPayloads(ctx, tenantId, payloadFiles, mode);
    Scenario scenario = importPackage(ctx, archive);
    Set<Inject> injects = injectService.findByScenarioId(scenario.getId());
    refuseOtherPayloads(injects, payloadIds);
    catalogScenarioTargets.addTo(injects, targets);
    markInstalled(scenario, identity, tenantId, released);
    changePublisher.changed(scenario, CatalogHistory.Action.INSTALL, mode);
    return scenario;
  }

  // -- STEPS --

  // Kept once uninstalled: reinstalling reuses it.
  private ScenarioIdentity uninstalledIdentity(
      String tenantId, InstallableScenarioVersion released) {
    ScenarioIdentity identity =
        scenarioIdentityRepository
            .findByTenantIdAndRepositoryAndExternalId(
                tenantId, released.repository(), released.externalId())
            .orElseGet(() -> newIdentity(tenantId, released));
    if (identity.getId() != null && scenarioService.isCatalogInstalled(identity)) {
      throw new BadRequestException(ALREADY_INSTALLED);
    }
    return identity;
  }

  private static ScenarioIdentity newIdentity(
      String tenantId, InstallableScenarioVersion released) {
    ScenarioIdentity identity = new ScenarioIdentity();
    identity.setTenant(new Tenant(tenantId));
    identity.setRepository(released.repository());
    identity.setExternalId(released.externalId());
    return identity;
  }

  private Set<String> installPayloads(
      TxCtx ctx,
      String tenantId,
      Map<InstallablePayloadVersion, byte[]> payloadFiles,
      CatalogHistory.Mode mode) {
    return payloadFiles.entrySet().stream()
        .map(
            file ->
                catalogPayloadService.installIfMissing(
                    ctx, tenantId, file.getKey(), file.getValue(), mode))
        .map(Payload::getId)
        .collect(Collectors.toSet());
  }

  private Scenario importPackage(TxCtx ctx, byte[] archive) throws Exception {
    ImportResult result =
        importService.handleInputStreamFileImport(
            ctx, new ByteArrayInputStream(archive), null, null, null, null, "");
    if (result.scenarioId() == null) {
      throw new BadRequestException("The package holds no scenario");
    }
    return scenarioService.scenario(ctx, result.scenarioId());
  }

  private static void refuseOtherPayloads(Set<Inject> injects, Set<String> payloadIds) {
    boolean other =
        injects.stream()
            .map(inject -> inject.getInjectorContract().map(InjectorContract::getPayload))
            .flatMap(Optional::stream)
            .anyMatch(payload -> !payloadIds.contains(payload.getId()));
    if (other) {
      throw new BadRequestException(OTHER_PAYLOAD_REFUSED);
    }
  }

  private void markInstalled(
      Scenario scenario,
      ScenarioIdentity identity,
      String tenantId,
      InstallableScenarioVersion released) {
    scenario.setCatalogIdentity(scenarioIdentityRepository.save(identity));
    scenario.setCatalogVersion(released.version());
    scenario.setAuthorUser(null);
    scenario.setAuthorOrganization(
        released.author() == null
            ? null
            : organizationService.findOrCreateByName(released.author(), tenantId));
  }
}
