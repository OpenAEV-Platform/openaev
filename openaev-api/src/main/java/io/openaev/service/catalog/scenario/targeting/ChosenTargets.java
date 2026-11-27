package io.openaev.service.catalog.scenario.targeting;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Base;
import io.openaev.database.model.Team;
import java.util.List;

/** The targets chosen at install, added to the injects that take their kind. */
public record ChosenTargets(
    List<Asset> assets, List<AssetGroup> assetGroups, List<Team> teams, List<String> hosts) {

  public TargetChoice choice() {
    return new TargetChoice(ids(assets), ids(assetGroups), ids(teams), hosts);
  }

  private static List<String> ids(List<? extends Base> targets) {
    return targets.stream().map(Base::getId).toList();
  }
}
