package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.scenario.ScenarioRevision;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.scenario.CatalogScenarioOfferService;
import io.openaev.service.catalog.scenario.InstallableScenarioVersion;
import io.openaev.service.catalog.scenario.ReleasedScenario;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Updates an installed catalogue scenario to a released version, or rolls it back to a revision:
 * reads the files it needs outside any transaction, then merges in one of its own.
 */
@Service
@RequiredArgsConstructor
public class CatalogScenarioUpdater {

  public static final String CHAINED_REFUSED = "Updating a chained scenario is not supported yet";
  public static final String IMPORTED_LINKS_REFUSED =
      "This version holds documents, channels or challenges: updating to it is not supported yet";

  private final InstalledScenarioService installedScenarioService;
  private final CatalogScenarioUpdateService catalogScenarioUpdateService;
  private final CatalogScenarioOfferService catalogScenarioOfferService;
  private final ScenarioRevisionService scenarioRevisionService;
  private final CatalogFileService catalogFileService;
  private final ScenarioContentReader reader;
  private final ObjectMapper mapper;

  // -- UPDATE --

  public ScenarioPreview previewUpdate(TxCtx ctx, String scenarioId, String version) {
    InstalledScenario installed = installedScenarioService.installed(ctx, scenarioId);
    return catalogScenarioUpdateService.preview(
        ctx, toRelease(ctx, installed, offered(ctx, installed, version)));
  }

  public void update(
      TxCtx ctx,
      String scenarioId,
      String version,
      Map<String, ScenarioMerge.Choice> choices,
      Map<String, String> edits) {
    InstalledScenario installed = installedScenarioService.installed(ctx, scenarioId);
    InstallableScenarioVersion released = offered(ctx, installed, version);
    catalogScenarioUpdateService.apply(
        ctx,
        toRelease(ctx, installed, released),
        catalogFileService.payloadFiles(released.payloads()),
        choices,
        edits);
  }

  // -- ROLLBACK --

  public ScenarioPreview previewRollback(TxCtx ctx, String scenarioId, String revisionId) {
    return catalogScenarioUpdateService.preview(ctx, toRevision(ctx, scenarioId, revisionId));
  }

  public void rollback(
      TxCtx ctx,
      String scenarioId,
      String revisionId,
      Map<String, ScenarioMerge.Choice> choices,
      Map<String, String> edits) {
    catalogScenarioUpdateService.apply(
        ctx, toRevision(ctx, scenarioId, revisionId), Map.of(), choices, edits);
  }

  // -- STEPS --

  private InstallableScenarioVersion offered(
      TxCtx ctx, InstalledScenario installed, String version) {
    InstallableScenarioVersion released =
        catalogScenarioOfferService.installable(ctx, installed.externalId(), version);
    if (!released.type().updatable()) {
      throw new BadRequestException(CHAINED_REFUSED);
    }
    return released;
  }

  private ScenarioUpdate toRelease(
      TxCtx ctx, InstalledScenario installed, InstallableScenarioVersion released) {
    ReleasedScenario files =
        catalogScenarioOfferService.released(ctx, installed.externalId(), released.version());
    ScenarioPackage scenarioPackage = packageOf(files);
    if (scenarioPackage.linksImportedContent()) {
      throw new BadRequestException(IMPORTED_LINKS_REFUSED);
    }
    return new ScenarioUpdate(
        installed,
        base(ctx, installed),
        reader.fromPackage(scenarioPackage, files.payloadVersions()),
        released.version(),
        ScenarioRevision.Reason.UPDATE);
  }

  private ScenarioUpdate toRevision(TxCtx ctx, String scenarioId, String revisionId) {
    InstalledScenario installed = installedScenarioService.installed(ctx, scenarioId);
    ScenarioRevision revision = scenarioRevisionService.revision(ctx, scenarioId, revisionId);
    return new ScenarioUpdate(
        installed,
        base(ctx, installed),
        revision.getContent(),
        revision.getCatalogVersion(),
        ScenarioRevision.Reason.ROLLBACK);
  }

  // What the installed version brought: the client's changes are measured against it.
  private ObjectNode base(TxCtx ctx, InstalledScenario installed) {
    ReleasedScenario released =
        catalogScenarioOfferService.released(ctx, installed.externalId(), installed.version());
    return reader.fromPackage(packageOf(released), released.payloadVersions());
  }

  private ScenarioPackage packageOf(ReleasedScenario released) {
    return ScenarioPackage.read(catalogFileService.storedFile(released.archiveHash()), mapper);
  }
}
