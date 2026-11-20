package io.openaev.service.catalog.scenario.targeting;

import io.openaev.database.model.Inject;
import java.util.List;

/** The kinds of target an inject takes and whether it has one. */
public record InjectTargets(Inject inject, List<TargetKind> kinds, boolean targeted) {

  public boolean isTargetable() {
    return !kinds.isEmpty();
  }
}
