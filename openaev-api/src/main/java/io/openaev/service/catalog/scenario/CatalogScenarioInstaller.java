package io.openaev.service.catalog.scenario;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.CatalogInstaller;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.LinkedHashMap;
import java.util.Map;
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

  /** Installs one released version with the targets an admin chose. */
  public Scenario install(
      TxCtx ctx, String tenantId, String externalId, String version, TargetChoice targets)
      throws Exception {
    return install(
        ctx,
        tenantId,
        catalogScenarioOfferService.installable(ctx, externalId, version),
        targets,
        CatalogHistory.Mode.MANUAL);
  }

  @Override
  protected CatalogOffer<InstallableScenarioVersion> offer() {
    return catalogScenarioOfferService;
  }

  @Override
  protected Scenario install(
      TxCtx ctx, String tenantId, InstallableScenarioVersion released, CatalogHistory.Mode mode)
      throws Exception {
    return install(ctx, tenantId, released, TargetChoice.NONE, mode);
  }

  private Scenario install(
      TxCtx ctx,
      String tenantId,
      InstallableScenarioVersion released,
      TargetChoice targets,
      CatalogHistory.Mode mode)
      throws Exception {
    Map<InstallablePayloadVersion, byte[]> payloadFiles = new LinkedHashMap<>();
    for (InstallablePayloadVersion payload : released.payloads()) {
      payloadFiles.put(payload, catalogFileService.storedFile(payload.fileHash()));
    }
    return catalogScenarioService.install(
        ctx,
        tenantId,
        released,
        catalogFileService.storedFile(released.archiveHash()),
        payloadFiles,
        targets,
        mode);
  }
}
