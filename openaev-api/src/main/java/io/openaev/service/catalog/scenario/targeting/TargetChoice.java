package io.openaev.service.catalog.scenario.targeting;

import io.openaev.service.catalog.scenario.CatalogScenarioType;
import io.openaev.service.catalog.scenario.ScenarioInstallChoice;
import java.util.List;

/** The targets chosen at install or launch, by ID, and hosts typed by hand. */
public record TargetChoice(
    List<String> assetIds, List<String> assetGroupIds, List<String> teamIds, List<String> hosts)
    implements ScenarioInstallChoice {

  /** No target: an install with no choice, by hand or by itself. */
  public static final TargetChoice NONE =
      new TargetChoice(List.of(), List.of(), List.of(), List.of());

  /** A choice from an input whose missing lists mean nothing chosen. */
  public static TargetChoice of(
      List<String> assetIds, List<String> assetGroupIds, List<String> teamIds, List<String> hosts) {
    return new TargetChoice(
        orEmpty(assetIds), orEmpty(assetGroupIds), orEmpty(teamIds), orEmpty(hosts));
  }

  public boolean isEmpty() {
    return assetIds.isEmpty() && assetGroupIds.isEmpty() && teamIds.isEmpty() && hosts.isEmpty();
  }

  private static List<String> orEmpty(List<String> values) {
    return values == null ? List.of() : values;
  }

  @Override
  public CatalogScenarioType type() {
    return CatalogScenarioType.TIME_BASED;
  }
}
