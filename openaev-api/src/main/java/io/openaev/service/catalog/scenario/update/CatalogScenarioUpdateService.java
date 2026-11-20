package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.VariableService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.event.CatalogChangePublisher;
import io.openaev.service.catalog.payload.CatalogPayloadService;
import io.openaev.service.catalog.payload.InstallablePayloadVersion;
import io.openaev.service.scenario.ScenarioService;
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

  public static final String NOT_INSTALLED = "This scenario was not installed from the catalogue";
  public static final String CHANGED_MEANWHILE = "The scenario changed meanwhile: try again";
  public static final String CONFLICT_LEFT = "Choose a side for each conflict";

  private final ScenarioService scenarioService;
  private final CatalogListService catalogListService;
  private final InjectService injectService;
  private final ExerciseService exerciseService;
  private final VariableService variableService;
  private final CatalogPayloadService catalogPayloadService;
  private final ScenarioRevisionService scenarioRevisionService;
  private final ScenarioContentReader reader;
  private final ScenarioContentWriter writer;
  private final CatalogChangePublisher changePublisher;

  // -- LIST --

  @Transactional(readOnly = true)
  public InstalledScenario installed(TxCtx ctx, String scenarioId) {
    return installedScenario(ctx, scenarioId);
  }

  @Transactional(readOnly = true)
  public ScenarioVersions versions(TxCtx ctx, String scenarioId) {
    InstalledScenario installed = installedScenario(ctx, scenarioId);
    List<CatalogScenario> versions =
        catalogListService.versions(CatalogScenario.class, installed.externalId());
    return new ScenarioVersions(
        versions,
        installed.version(),
        latestOffered(versions),
        exerciseService.countByScenario(installed.tenantId(), installed.scenarioId()));
  }

  private static String latestOffered(List<CatalogScenario> newestFirst) {
    return newestFirst.stream()
        .filter(version -> version.getStatus().isOffered())
        .map(CatalogScenario::getVersion)
        .findFirst()
        .orElse(null);
  }

  private InstalledScenario installedScenario(TxCtx ctx, String scenarioId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    if (scenario.getCatalogIdentity() == null) {
      throw new BadRequestException(NOT_INSTALLED);
    }
    return new InstalledScenario(
        scenario.getId(),
        scenario.getTenant().getId(),
        scenario.getCatalogExternalId(),
        scenario.getCatalogVersion());
  }

  /** What the content changes, and its conflicts, none of them chosen yet. */
  @Transactional(readOnly = true)
  public ScenarioMerge.Result preview(TxCtx ctx, ScenarioUpdate update) {
    Scenario scenario = unchanged(ctx, update);
    return ScenarioMerge.merge(
        update.base(),
        update.applied(),
        local(scenario, injectService.findByScenarioId(scenario.getId())),
        Map.of());
  }

  // -- UPDATE --

  /** Installs the payload versions the content runs, then merges it with the choices given. */
  @Transactional(rollbackFor = Exception.class)
  public void apply(
      TxCtx ctx,
      ScenarioUpdate update,
      Map<InstallablePayloadVersion, byte[]> payloadFiles,
      Map<String, ScenarioMerge.Choice> choices) {
    payloadFiles.forEach(
        (released, file) ->
            catalogPayloadService.installIfMissing(
                ctx, update.installed().tenantId(), released, file, CatalogHistory.Mode.MANUAL));
    Scenario scenario = unchanged(ctx, update);
    Collection<Inject> injects = injectService.findByScenarioId(scenario.getId());
    ObjectNode local = local(scenario, injects);
    ScenarioMerge.Result merge =
        ScenarioMerge.merge(update.base(), update.applied(), local, choices);
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

  private ObjectNode local(Scenario scenario, Collection<Inject> injects) {
    return reader.fromEntities(
        scenario, variableService.variablesFromScenario(scenario.getId()), injects);
  }
}
