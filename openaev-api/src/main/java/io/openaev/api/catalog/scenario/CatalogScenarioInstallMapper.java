package io.openaev.api.catalog.scenario;

import io.openaev.api.catalog.dto.scenario.CatalogScenarioInstallInput;
import io.openaev.api.catalog.dto.scenario.ChainedScenarioInstallInput;
import io.openaev.api.catalog.dto.scenario.TimeBasedScenarioInstallInput;
import io.openaev.service.catalog.scenario.ScenarioInstallChoice;
import io.openaev.service.catalog.scenario.WorkflowChoice;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;

/** The choice of the service from the install input of the API, one per scenario type. */
final class CatalogScenarioInstallMapper {

  private CatalogScenarioInstallMapper() {}

  // No body: the service applies the empty choice of the scenario's own type.
  static ScenarioInstallChoice toChoice(CatalogScenarioInstallInput input) {
    return switch (input) {
      case null -> null;
      case TimeBasedScenarioInstallInput targets ->
          TargetChoice.of(
              targets.assetIds(),
              targets.assetGroupIds(),
              targets.teamIds(),
              targets.manualTargets());
      case ChainedScenarioInstallInput workflow ->
          new WorkflowChoice(workflow.workflowConfiguration());
    };
  }
}
