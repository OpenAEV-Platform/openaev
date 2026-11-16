package io.openaev.service.catalog.scenario;

import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.catalog.scenario.targeting.CatalogScenarioTargets;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A time-based scenario runs its payloads in its injects, which take the chosen targets. */
@Component
@RequiredArgsConstructor
public class TimeBasedScenarioInstall implements CatalogScenarioTypeInstall<TargetChoice> {

  private final InjectService injectService;
  private final CatalogScenarioTargets catalogScenarioTargets;

  @Override
  public CatalogScenarioType type() {
    return CatalogScenarioType.TIME_BASED;
  }

  @Override
  public TargetChoice noChoice() {
    return TargetChoice.NONE;
  }

  @Override
  public Stream<Payload> payloadsRun(Scenario scenario) {
    return injectService.findByScenarioId(scenario.getId()).stream()
        .map(inject -> inject.getInjectorContract().map(InjectorContract::getPayload))
        .flatMap(Optional::stream);
  }

  @Override
  public void apply(Scenario scenario, TargetChoice choice) {
    catalogScenarioTargets.addTo(injectService.findByScenarioId(scenario.getId()), choice);
  }
}
