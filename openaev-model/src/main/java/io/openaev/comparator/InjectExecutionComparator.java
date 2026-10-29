package io.openaev.comparator;

import io.openaev.database.model.Inject;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

public final class InjectExecutionComparator implements Comparator<Inject> {
  private final Instant nowReference;

  public InjectExecutionComparator(Instant nowReference) {
    this.nowReference = nowReference;
  }

  @Override
  public int compare(Inject o1, Inject o2) {
    Optional<Instant> instantO1 = o1.getDate(nowReference);
    Optional<Instant> instantO2 = o2.getDate(nowReference);

    if (instantO1.isPresent() && instantO2.isPresent()) {
      return instantO1.get().compareTo(instantO2.get());
    }

    if (instantO1.isPresent()) return 1;
    if (instantO2.isPresent()) return -1;

    if (o1.getId() != null && o2.getId() != null) {
      return o1.getId().compareTo(o2.getId());
    }

    if (o1.getId() != null) return 1;
    if (o2.getId() != null) return -1;

    return 0;
  }
}
