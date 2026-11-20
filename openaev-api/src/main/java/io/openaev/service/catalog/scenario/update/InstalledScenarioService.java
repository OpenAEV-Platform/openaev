package io.openaev.service.catalog.scenario.update;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.scenario.ScenarioService;
import java.util.List;
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

  // -- LIST --

  @Transactional(readOnly = true)
  public InstalledScenario installed(TxCtx ctx, String scenarioId) {
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

  @Transactional(readOnly = true)
  public ScenarioVersions versions(TxCtx ctx, String scenarioId) {
    InstalledScenario installed = installed(ctx, scenarioId);
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
}
