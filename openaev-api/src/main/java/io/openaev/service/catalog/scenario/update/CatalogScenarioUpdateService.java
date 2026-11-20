package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.event.CatalogChangePublisher;
import io.openaev.service.catalog.payload.CatalogPayloadService;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Collection;
import java.util.List;
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
  public static final String CONFLICT_LEFT = "Choose a side for each conflict";

  private final ScenarioService scenarioService;
  private final CatalogListService catalogListService;
  private final InjectService injectService;
  private final CatalogPayloadService catalogPayloadService;
  private final ScenarioRevisionService scenarioRevisionService;
  private final ScenarioPayloadCheckService scenarioPayloadCheckService;
  private final ScenarioContentWriter writer;
  private final CatalogChangePublisher changePublisher;

  // -- LIST --

  /** What the content changes, and its conflicts, none of them chosen yet. */
  @Transactional(readOnly = true)
  public ScenarioPreview preview(TxCtx ctx, ScenarioUpdate update) {
    Scenario scenario = unchanged(ctx, update);
    ScenarioMerge.Result merge =
        ScenarioMerge.merge(
            update.base(),
            update.applied(),
            scenarioRevisionService.content(
                scenario, injectService.findByScenarioId(scenario.getId())),
            Map.of());
    return new ScenarioPreview(
        merge,
        scenarioPayloadCheckService.check(update.installed().tenantId(), merge.merged()),
        notes(update));
  }

  // The versions after the installed one up to the one applied, with a changelog.
  private List<CatalogScenario> notes(ScenarioUpdate update) {
    InstalledScenario installed = update.installed();
    return catalogListService.versions(CatalogScenario.class, installed.externalId()).stream()
        .filter(version -> version.getChangelog() != null)
        .filter(
            version -> SemanticVersion.ORDER.compare(version.getVersion(), installed.version()) > 0)
        .filter(
            version -> SemanticVersion.ORDER.compare(version.getVersion(), update.version()) <= 0)
        .toList();
  }

  // -- UPDATE --

  /** Installs the payload versions the content runs, then merges it with the choices given. */
  @Transactional(rollbackFor = Exception.class)
  public void apply(
      TxCtx ctx,
      ScenarioUpdate update,
      Map<InstallablePayloadVersion, byte[]> payloadFiles,
      Map<String, ScenarioMerge.Choice> choices,
      Map<String, String> edits) {
    payloadFiles.forEach(
        (released, file) ->
            catalogPayloadService.installIfMissing(
                ctx, update.installed().tenantId(), released, file, CatalogHistory.Mode.MANUAL));
    Scenario scenario = unchanged(ctx, update);
    Collection<Inject> injects = injectService.findByScenarioId(scenario.getId());
    ObjectNode local = scenarioRevisionService.content(scenario, injects);
    ScenarioMerge.Result merge =
        ScenarioMerge.merge(update.base(), update.applied(), local, choices, edits);
    if (!merge.resolved()) {
      throw new BadRequestException(CONFLICT_LEFT);
    }
    scenarioRevisionService.keep(scenario, update.reason(), local);
    writer.write(scenario, injects, merge.merged());
    scenario.setCatalogVersion(update.version());
    changePublisher.changed(scenario, update.reason().action(), CatalogHistory.Mode.MANUAL);
  }

  // -- STEPS --

  // The base was read for the version installed when the update started.
  private Scenario unchanged(TxCtx ctx, ScenarioUpdate update) {
    Scenario scenario = scenarioService.scenario(ctx, update.installed().scenarioId());
    if (!Objects.equals(scenario.getCatalogVersion(), update.installed().version())) {
      throw new BadRequestException(CHANGED_MEANWHILE);
    }
    return scenario;
  }
}
