package io.openaev.debug;

/**
 * Thread-local {@link RequestQueryStats} for the current request. The filter calls {@link #start} /
 * {@link #clear}; the SQL listener calls {@link #record} (a no-op when no request is active).
 */
public final class OrmInsightContext {

  private static final ThreadLocal<RequestQueryStats> CURRENT = new ThreadLocal<>();

  private OrmInsightContext() {}

  public static void start(String requestDescription) {
    CURRENT.set(new RequestQueryStats(requestDescription));
  }

  public static RequestQueryStats current() {
    return CURRENT.get();
  }

  /** No-op unless a request is active on this thread. */
  public static void record(String sql, long elapsedMillis) {
    RequestQueryStats stats = CURRENT.get();
    if (stats != null) {
      stats.record(sql, elapsedMillis);
    }
  }

  public static void clear() {
    CURRENT.remove();
  }
}
