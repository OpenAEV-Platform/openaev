package io.openaev.service.catalog.scenario;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.scenario.ScenarioIdentity;
import io.openaev.database.repository.ScenarioIdentityRepository;
import io.openaev.importer.ImportResult;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.ImportService;
import io.openaev.service.catalog.event.CatalogChangePublisher;
import io.openaev.service.catalog.payload.CatalogPayloadService;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.organization.OrganizationService;
import io.openaev.service.scenario.ScenarioService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  public static final String OTHER_TYPE_CHOICE_REFUSED =
      "The choice is for another type of scenario than the one released";
  public static final String OTHER_PAYLOAD_REFUSED =
      "The scenario runs a payload its release does not name";

  private final ScenarioService scenarioService;
  private final ScenarioIdentityRepository scenarioIdentityRepository;
  private final ImportService importService;
  private final CatalogPayloadService catalogPayloadService;
  private final List<CatalogScenarioTypeInstall<?>> typeInstalls;
  private final OrganizationService organizationService;
  private final CatalogChangePublisher changePublisher;

  // -- CREATE --

  /**
   * Installs the payload versions the release names, then imports the package, whose payloads link
   * to these versions by external ID and content, and applies what the admin chose: targets for the
   * injects, scope and limits for a chained scenario's workflow.
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario install(
      TxCtx ctx,
      String tenantId,
      InstallableScenarioVersion released,
      byte[] archive,
      Map<InstallablePayloadVersion, byte[]> payloadFiles,
      ScenarioInstallChoice choice,
      CatalogHistory.Mode mode)
      throws Exception {
    ScenarioIdentity identity = uninstalledIdentity(tenantId, released);
    Set<String> payloadIds = installPayloads(ctx, tenantId, payloadFiles, mode);
    Scenario scenario = importPackage(ctx, archive);
    CatalogScenarioTypeInstall<ScenarioInstallChoice> typeInstall = typeInstall(released.type());
    refuseOtherPayloads(typeInstall.payloadsRun(scenario), payloadIds);
    typeInstall.apply(scenario, choiceFor(typeInstall, choice));
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

  // Each install takes the choice of its own type, checked by choiceFor.
  @SuppressWarnings("unchecked")
  private CatalogScenarioTypeInstall<ScenarioInstallChoice> typeInstall(CatalogScenarioType type) {
    return typeInstalls.stream()
        .filter(install -> install.type() == type)
        .map(install -> (CatalogScenarioTypeInstall<ScenarioInstallChoice>) install)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("No install for " + type + " scenarios"));
  }

  private static ScenarioInstallChoice choiceFor(
      CatalogScenarioTypeInstall<ScenarioInstallChoice> typeInstall, ScenarioInstallChoice choice) {
    if (choice == null) {
      return typeInstall.noChoice();
    }
    if (choice.type() != typeInstall.type()) {
      throw new BadRequestException(OTHER_TYPE_CHOICE_REFUSED);
    }
    return choice;
  }

  private static void refuseOtherPayloads(Stream<Payload> payloads, Set<String> payloadIds) {
    if (payloads.anyMatch(payload -> !payloadIds.contains(payload.getId()))) {
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
