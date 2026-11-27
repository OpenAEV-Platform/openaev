package io.openaev.service.catalog.scenario.update;

import static java.util.stream.Collectors.toSet;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.raw.RawCatalogInstalledScenario;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogRevocations;
import io.openaev.service.scenario.ScenarioService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A scenario installed from the catalogue, and the catalogue versions it can move to. */
@Service
@RequiredArgsConstructor
public class InstalledScenarioService {

  public static final String NOT_INSTALLED = "This scenario was not installed from the catalogue";

  private final ScenarioService scenarioService;
  private final CatalogListService catalogListService;
  private final ExerciseService exerciseService;
  private final ScenarioRevisionService scenarioRevisionService;

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

  /** The catalogue state of each of these scenarios the caller reads and installed. */
  @Transactional(readOnly = true)
  public Map<String, ScenarioCatalogState> catalogStates(
      TxCtx ctx, String tenantId, List<String> scenarioIds) {
    Set<String> readable =
        scenarioService.scenarios(ctx, scenarioIds).stream()
            .map(ScenarioSimple::getId)
            .collect(toSet());
    List<RawCatalogInstalledScenario> installed =
        scenarioService.installedFromCatalog(tenantId).stream()
            .filter(scenario -> readable.contains(scenario.getScenarioId()))
            .toList();
    if (installed.isEmpty()) {
      return Map.of();
    }
    CatalogRevocations revocations = revocations(installed);
    Map<String, NumberedRevision> lastUpdates =
        scenarioRevisionService.lastUpdates(
            tenantId, installed.stream().map(RawCatalogInstalledScenario::getScenarioId).toList());
    Map<String, ScenarioCatalogState> states = new HashMap<>();
    for (RawCatalogInstalledScenario scenario : installed) {
      states.put(
          scenario.getScenarioId(),
          new ScenarioCatalogState(
              revocations.revokes(scenario.getExternalId(), scenario.getVersion()),
              lastUpdates.get(scenario.getScenarioId())));
    }
    return states;
  }

  private CatalogRevocations revocations(List<RawCatalogInstalledScenario> installed) {
    return CatalogRevocations.of(
        catalogListService.versions(
            CatalogScenario.class,
            installed.stream().map(RawCatalogInstalledScenario::getExternalId).toList()));
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

  private static String latestOffered(List<CatalogScenario> newestFirst) {
    return newestFirst.stream()
        .filter(version -> version.getStatus().isOffered())
        .map(CatalogScenario::getVersion)
        .findFirst()
        .orElse(null);
  }
}
