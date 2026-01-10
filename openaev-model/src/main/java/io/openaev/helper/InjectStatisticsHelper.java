package io.openaev.helper;

import io.openaev.database.model.Inject;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class for computing inject execution statistics.
 *
 * <p>This utility provides aggregated statistics for a collection of injects, useful for dashboard
 * displays and progress tracking during exercises and simulations.
 *
 * @see Inject
 */
public class InjectStatisticsHelper {

  private InjectStatisticsHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Computes aggregated statistics for a list of injects.
   *
   * <p>The returned map contains the following statistics:
   *
   * <ul>
   *   <li>{@code total_count} - Total number of injects
   *   <li>{@code total_executed} - Number of injects that have been executed
   *   <li>{@code total_remaining} - Number of injects not yet executed
   *   <li>{@code total_past} - Number of injects scheduled in the past
   *   <li>{@code total_future} - Number of injects scheduled in the future
   *   <li>{@code total_progress} - Execution progress percentage (0-100)
   * </ul>
   *
   * @param injects the list of injects to analyze (must not be null)
   * @return a map containing the computed statistics
   */
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
