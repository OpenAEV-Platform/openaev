package io.openaev.service.catalog.scenario.targeting;

import io.openaev.database.model.Inject;
import java.util.List;

/** The kinds of target an inject needs and whether it has them. */
public record InjectTargets(Inject inject, List<TargetKind> kinds, boolean targeted) {

  public boolean isTargetable() {
    return !kinds.isEmpty();
  }
}
