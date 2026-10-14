package io.openaev.config;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

/**
 * Records the statements executed between {@link #start()} and {@link #stop()}, so a test can ask
 * whether a window of work that should only read issued an {@code UPDATE} on a given table.
 *
 * <p>This is the measurement behind the dirty-on-load family: a JSON-mapped field whose loaded
 * value does not compare equal to its own snapshot makes Hibernate consider the entity changed on
 * every load, so a plain read flushes an {@code UPDATE} of the row it just read. On a tenant-active
 * table that write is rewritten by the statement inspector and can match zero rows, which turns the
 * read into a 500 rather than a harmless extra statement.
 *
 * <p>It observes statements at the datasource, through datasource-proxy, for the same reason the
 * fail-closed detector does: that is the only place every statement is visible with the SQL
 * actually sent, whatever issued it.
 */
public final class JsonTypeUpdateOnLoadRecorder implements QueryExecutionListener {

  private final List<String> captured = new CopyOnWriteArrayList<>();
  private volatile boolean recording;

  /** Starts a fresh recording window, dropping anything captured before. */
  public void start() {
    captured.clear();
    recording = true;
  }

  public void stop() {
    recording = false;
  }

  /** Every statement captured in the window, in execution order. */
  public List<String> captured() {
    return List.copyOf(captured);
  }

  /**
   * The captured statements that update {@code table}. Matched on the statement's leading {@code
   * update <table>}, with or without the dialect's quoting, so an update of another table that
   * merely mentions this one in a subquery is not counted.
   */
  public List<String> updatesOn(String table) {
    Pattern updateOfTable =
        Pattern.compile("^update\\s+(\"?)" + Pattern.quote(table) + "\\1(\\s|$)");
    return captured.stream()
        .map(sql -> sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT))
        .filter(sql -> updateOfTable.matcher(sql).find())
        .toList();
  }

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // Captured after execution, so a statement that failed is still recorded with its SQL.
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    if (!recording) {
      return;
    }
    for (QueryInfo query : queryInfoList) {
      if (query.getQuery() != null) {
        captured.add(query.getQuery());
      }
    }
  }
}
