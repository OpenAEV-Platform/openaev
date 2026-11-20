package io.openaev.api.scenario;

import io.openaev.api.scenario.dto.ScenarioChangeOutput;
import io.openaev.api.scenario.dto.ScenarioRevisionOutput;
import io.openaev.database.model.scenario.ScenarioRevision;
import io.openaev.service.catalog.scenario.update.ScenarioMerge;

public class ScenarioUpdateMapper {

  private ScenarioUpdateMapper() {}

  public static ScenarioChangeOutput toOutput(ScenarioMerge.Change change) {
    return new ScenarioChangeOutput(
        change.id(),
        change.element(),
        change.field(),
        change.title(),
        change.status(),
        change.base(),
        change.applied(),
        change.local());
  }

  public static ScenarioRevisionOutput toOutput(ScenarioRevision revision) {
    return new ScenarioRevisionOutput(
        revision.getId(),
        revision.getReason(),
        revision.getCatalogVersion(),
        revision.getUser() == null ? null : revision.getUser().getName(),
        revision.getCreatedAt());
  }
}
