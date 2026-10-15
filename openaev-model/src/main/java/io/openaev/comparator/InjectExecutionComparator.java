package io.openaev.comparator;

import io.openaev.database.model.Inject;
import java.time.Instant;
import java.util.Comparator;

public final class InjectExecutionComparator implements Comparator<Inject> {
  private final Instant nowReference;

  public InjectExecutionComparator(Instant nowReference) {
    this.nowReference = nowReference;
  }

  @Override
  public int compare(Inject o1, Inject o2) {
    if (o1.getDate(nowReference).isPresent() && o2.getDate(nowReference).isPresent()) {
      return o1.getDate(nowReference).get().compareTo(o2.getDate(nowReference).get());
    }
    if (o1.getDate(nowReference).isPresent() && o2.getDate(nowReference).isEmpty()) return 1;
    if (o1.getDate(nowReference).isEmpty() && o2.getDate(nowReference).isPresent()) return -1;
    if (o1.getId() != null && o2.getId() != null) {
      return o1.getId().compareTo(o2.getId());
    }
    if (o1.getId() != null) return 1;
    if (o2.getId() != null) return -1;
    return 0;
  }
}
