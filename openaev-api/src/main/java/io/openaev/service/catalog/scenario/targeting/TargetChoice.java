package io.openaev.service.catalog.scenario.targeting;

import io.openaev.service.catalog.scenario.CatalogScenarioType;
import io.openaev.service.catalog.scenario.ScenarioInstallChoice;
import java.util.List;

/** The targets an admin chooses for a catalogue scenario, by ID, and hosts typed by hand. */
public record TargetChoice(
    List<String> assetIds, List<String> assetGroupIds, List<String> teamIds, List<String> hosts)
    implements ScenarioInstallChoice {

  /** No target: an install with no choice, by hand or by itself. */
  public static final TargetChoice NONE =
      new TargetChoice(List.of(), List.of(), List.of(), List.of());

  @Override
  public CatalogScenarioType type() {
    return CatalogScenarioType.TIME_BASED;
  }
}
