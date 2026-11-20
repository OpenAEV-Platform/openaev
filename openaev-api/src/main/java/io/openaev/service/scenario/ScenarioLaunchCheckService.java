package io.openaev.service.scenario;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.catalog.scenario.CatalogScenarioOfferService;
import io.openaev.service.catalog.scenario.targeting.CatalogScenarioTargets;
import io.openaev.service.catalog.scenario.targeting.InjectTargets;
import io.openaev.service.payload.PayloadRevocationService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What stops a scenario from launching, or makes its injects fail, read before the launch. */
@Service
@RequiredArgsConstructor
public class ScenarioLaunchCheckService {

  private static final Comparator<Inject> RUN_ORDER =
      Comparator.comparing(Inject::getDependsDuration, Comparator.nullsFirst(Long::compareTo))
          .thenComparing(Inject::getTitle, Comparator.nullsLast(String::compareTo));

  private final ScenarioService scenarioService;
  private final InjectService injectService;
  private final CatalogScenarioTargets catalogScenarioTargets;
  private final PayloadRevocationService payloadRevocationService;
  private final CatalogScenarioOfferService catalogScenarioOfferService;

  // A disabled inject never runs: it never blocks a launch.
  @Transactional(readOnly = true)
  public LaunchCheck check(TxCtx ctx, String scenarioId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    List<Inject> injects =
        injectService.findByScenarioId(scenarioId).stream()
            .filter(Inject::isEnabled)
            .sorted(RUN_ORDER)
            .toList();
    return new LaunchCheck(
        targetable(injects),
        revoked(scenario.getTenant().getId(), injects),
        catalogScenarioOfferService.missingToRun(scenario));
  }

  private List<InjectTargets> targetable(List<Inject> injects) {
    return injects.stream()
        .map(catalogScenarioTargets::targetsOf)
        .filter(InjectTargets::isTargetable)
        .toList();
  }

  private List<Inject> revoked(String tenantId, List<Inject> injects) {
    List<Payload> payloads =
        injects.stream().flatMap(inject -> inject.getPayload().stream()).distinct().toList();
    if (payloads.isEmpty()) {
      return List.of();
    }
    Set<String> revoked = payloadRevocationService.revocations(tenantId, payloads).keySet();
    return injects.stream()
        .filter(
            inject -> inject.getPayload().map(Payload::getId).filter(revoked::contains).isPresent())
        .toList();
  }
}
