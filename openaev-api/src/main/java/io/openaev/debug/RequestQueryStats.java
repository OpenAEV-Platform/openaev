package io.openaev.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-request SQL aggregation for the ORM summary. Grouped by the parameterised SQL (so N+1
 * occurrences collapse onto one key). Single-thread use (see {@link OrmInsightContext}).
 */
public final class RequestQueryStats {

  /** One distinct statement and how many times / how long it ran during the request. */
  public record StatementStat(String sql, int count, long totalMillis, boolean select) {}

  private final String requestDescription;
  // value = [count, totalMillis]
  private final Map<String, long[]> perStatement = new LinkedHashMap<>();
  private int totalQueries;
  private long totalMillis;

  public RequestQueryStats(String requestDescription) {
    this.requestDescription = requestDescription;
  }

  /** Records one statement execution. A batch counts as a single execution, not as N. */
  public void record(String sql, long elapsedMillis) {
    if (sql == null) {
      return;
    }
    long millis = Math.max(0, elapsedMillis);
    totalQueries++;
    totalMillis += millis;
    long[] agg = perStatement.computeIfAbsent(sql, k -> new long[2]);
    agg[0]++;
    agg[1] += millis;
  }

  public String requestDescription() {
    return requestDescription;
  }

  public int totalQueries() {
    return totalQueries;
  }

  public long totalMillis() {
    return totalMillis;
  }

  public int distinctStatements() {
    return perStatement.size();
  }

  /** Statements executed strictly more than {@code threshold} times, most frequent first. */
  public List<StatementStat> repeatedStatements(int threshold) {
    List<StatementStat> result = new ArrayList<>();
    for (Map.Entry<String, long[]> entry : perStatement.entrySet()) {
      int count = (int) entry.getValue()[0];
      if (count > threshold) {
        result.add(
            new StatementStat(
                entry.getKey(), count, entry.getValue()[1], isSelect(entry.getKey())));
      }
    }
    result.sort(Comparator.comparingInt(StatementStat::count).reversed());
    return result;
  }

  private static boolean isSelect(String sql) {
    return sql.stripLeading().regionMatches(true, 0, "select", 0, 6);
  }
}
