package io.openaev.api.catalog.dto.scenario;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.openaev.api.scenario.dto.ScenarioTargetsInput;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;

/** Targets added to every inject of the installed scenario that takes their kind. */
public record TimeBasedScenarioInstallInput(@JsonUnwrapped ScenarioTargetsInput targets)
    implements CatalogScenarioInstallInput {

  @Override
  public TargetChoice toChoice() {
    return ScenarioTargetsInput.choiceOf(targets);
  }
}
