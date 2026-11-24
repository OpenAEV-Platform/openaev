package io.openaev.api.scenario;

import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.scenario.dto.InjectTargetsOutput;
import io.openaev.api.scenario.dto.RevokedInjectOutput;
import io.openaev.api.scenario.dto.ScenarioLaunchCheckOutput;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.service.catalog.scenario.targeting.InjectTargets;
import io.openaev.service.scenario.LaunchCheck;

public class ScenarioLaunchCheckMapper {

  private ScenarioLaunchCheckMapper() {}

  public static ScenarioLaunchCheckOutput toOutput(LaunchCheck check) {
    return new ScenarioLaunchCheckOutput(
        check.injects().stream().map(ScenarioLaunchCheckMapper::toOutput).toList(),
        check.revokedInjects().stream().map(ScenarioLaunchCheckMapper::toRevokedOutput).toList(),
        check.missingRequirements().stream().map(CatalogItemMapper::toRequirementOutput).toList());
  }

  private static InjectTargetsOutput toOutput(InjectTargets targets) {
    Inject inject = targets.inject();
    return new InjectTargetsOutput(
        inject.getId(), inject.getTitle(), targets.kinds(), targets.targeted());
  }

  private static RevokedInjectOutput toRevokedOutput(Inject inject) {
    return new RevokedInjectOutput(
        inject.getId(),
        inject.getTitle(),
        inject.getInjectorContract().map(InjectorContract::getId).orElseThrow());
  }
}
