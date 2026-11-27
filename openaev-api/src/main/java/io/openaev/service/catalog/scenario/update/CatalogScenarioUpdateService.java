package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ConflictException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.event.ContentChangePublisher;
import io.openaev.service.catalog.merge.MergeChoice;
import io.openaev.service.catalog.payload.CatalogPayloadService;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.scenario.ScenarioService;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merges content into an installed catalogue scenario: the client's own changes stay, each conflict
 * takes the side he chose, and the content it had is kept as a revision first.
 */
@Service
@RequiredArgsConstructor
public class CatalogScenarioUpdateService {

  public static final String CHANGED_MEANWHILE = "The scenario changed meanwhile: try again";
  public static final String INSTALLED_BEFORE_UPDATES =
      "This scenario was installed before updates were supported: it cannot be updated or rolled"
          + " back";

  private final ScenarioService scenarioService;
  private final CatalogListService catalogListService;
  private final InjectService injectService;
  private final CatalogPayloadService catalogPayloadService;
  private final ScenarioRevisionService scenarioRevisionService;
  private final ScenarioPayloadCheckService scenarioPayloadCheckService;
  private final ScenarioContentWriter writer;
  private final ContentChangePublisher changePublisher;

  // -- LIST --

  /** What the content changes, and its conflicts, none of them chosen yet. */
  @Transactional(readOnly = true)
  public ScenarioPreview preview(TxCtx ctx, ScenarioUpdate update) {
    Scenario scenario = unchanged(ctx, update, update.installed().version());
    ScenarioMerge.Result merge =
        ScenarioMerge.merge(
            update.base(),
            update.applied(),
            scenarioRevisionService.content(scenario, trackedInjects(scenario)),
            Map.of());
    return new ScenarioPreview(
        update.installed().version(),
        merge,
        scenarioPayloadCheckService.check(update.installed().tenantId(), merge.merged()),
        CatalogListService.releaseNotes(
            catalogListService.versions(CatalogScenario.class, update.installed().externalId()),
            update.installed().version(),
            update.version()));
  }

  // -- UPDATE --

  /** Installs the payload versions the content runs, then merges it with the choices given. */
  @Transactional(rollbackFor = Exception.class)
  public void apply(
      TxCtx ctx,
      ScenarioUpdate update,
      String previewedVersion,
      Map<InstallablePayloadVersion, byte[]> payloadFiles,
      Map<String, MergeChoice> choices,
      Map<String, String> edits) {
    payloadFiles.forEach(
        (released, file) ->
            catalogPayloadService.installIfMissing(
                ctx, update.installed().tenantId(), released, file, ContentChange.Mode.MANUAL));
    Scenario scenario = unchanged(ctx, update, previewedVersion);
    Collection<Inject> injects = trackedInjects(scenario);
    ObjectNode local = scenarioRevisionService.content(scenario, injects);
    ScenarioMerge.Result merge =
        ScenarioMerge.merge(update.base(), update.applied(), local, choices, edits);
    if (!merge.resolved()) {
      throw new ConflictException(MergeChoice.MISSING);
    }
    scenarioRevisionService.keep(scenario, update.reason(), local);
    writer.write(scenario, injects, merge.merged());
    scenario.setCatalogVersion(update.version());
    changePublisher.changed(scenario, update.reason().action(), ContentChange.Mode.MANUAL);
  }

  // -- STEPS --

  // The base and the preview were read for this version: the scenario must still run it.
  private Scenario unchanged(TxCtx ctx, ScenarioUpdate update, String version) {
    Scenario scenario = scenarioService.scenario(ctx, update.installed().scenarioId());
    if (!Objects.equals(scenario.getCatalogVersion(), update.installed().version())
        || !Objects.equals(scenario.getCatalogVersion(), version)) {
      throw new ConflictException(CHANGED_MEANWHILE);
    }
    return scenario;
  }

  // Installed before the injects kept their package ID: the base cannot be matched to them.
  private Collection<Inject> trackedInjects(Scenario scenario) {
    Collection<Inject> injects = injectService.findByScenarioId(scenario.getId());
    boolean untracked =
        !injects.isEmpty() && injects.stream().allMatch(inject -> inject.getCatalogId() == null);
    if (untracked || !scenarioRevisionService.hasInstallRevision(scenario)) {
      throw new BadRequestException(INSTALLED_BEFORE_UPDATES);
    }
    return injects;
  }
}
