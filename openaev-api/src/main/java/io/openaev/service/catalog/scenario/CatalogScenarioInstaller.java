package io.openaev.service.catalog.scenario;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Installs catalogue scenarios, reading the released package and payload files from storage outside
 * any transaction, then installing them in one.
 */
@Service
@RequiredArgsConstructor
public class CatalogScenarioInstaller {

  private final CatalogScenarioOfferService catalogScenarioOfferService;
  private final CatalogScenarioService catalogScenarioService;
  private final CatalogFileService catalogFileService;

  public Scenario install(
      TxCtx ctx, String tenantId, String externalId, String version, TargetChoice targets)
      throws Exception {
    InstallableScenarioVersion released =
        catalogScenarioOfferService.installable(ctx, externalId, version);
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
        targets);
  }
}
