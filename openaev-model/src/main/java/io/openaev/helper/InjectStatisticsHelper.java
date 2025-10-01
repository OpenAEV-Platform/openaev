package io.openaev.helper;

import io.openaev.database.model.Inject;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InjectStatisticsHelper {

  public static Map<String, Long> getInjectStatistics(@NotNull final List<Inject> injects) {
    Map<String, Long> stats = new HashMap<>();
    long total = injects.size();
    stats.put("total_count", total);
    long executed = injects.stream().filter(inject -> inject.getStatus().isPresent()).count();
    stats.put("total_executed", executed);
    stats.put("total_remaining", injects.stream().filter(Inject::isNotExecuted).count());
    stats.put("total_past", injects.stream().filter(Inject::isPastInject).count());
    stats.put("total_future", injects.stream().filter(Inject::isFutureInject).count());
    stats.put("total_progress", total > 0 ? (executed * 100 / total) : 0);
    return stats;
  }
}
