package io.openaev.service.catalog.scenario;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.CatalogInstaller;
import io.openaev.service.catalog.CatalogOffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Installs catalogue scenarios, each version in a transaction of its own. */
@Service
@RequiredArgsConstructor
public class CatalogScenarioInstaller
    extends CatalogInstaller<InstallableScenarioVersion, Scenario> {

  private final CatalogScenarioOfferService catalogScenarioOfferService;
  private final CatalogScenarioService catalogScenarioService;
  private final CatalogFileService catalogFileService;

  /** Installs one released version with what an admin chose, if anything. */
  public Scenario install(
      TxCtx ctx, String tenantId, String externalId, String version, ScenarioInstallChoice choice)
      throws Exception {
    return install(
        ctx,
        tenantId,
        catalogScenarioOfferService.installable(ctx, externalId, version),
        choice,
        ContentChange.Mode.MANUAL);
  }

  @Override
  protected CatalogOffer<InstallableScenarioVersion> offer() {
    return catalogScenarioOfferService;
  }

  @Override
  protected Scenario install(
      TxCtx ctx, String tenantId, InstallableScenarioVersion released, ContentChange.Mode mode)
      throws Exception {
    return install(ctx, tenantId, released, null, mode);
  }

  private Scenario install(
      TxCtx ctx,
      String tenantId,
      InstallableScenarioVersion released,
      ScenarioInstallChoice choice,
      ContentChange.Mode mode)
      throws Exception {
    return catalogScenarioService.install(
        ctx,
        tenantId,
        released,
        catalogFileService.storedFile(released.archiveHash()),
        catalogFileService.payloadFiles(released.payloads()),
        choice,
        mode);
  }
}
