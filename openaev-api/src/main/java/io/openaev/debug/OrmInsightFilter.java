package io.openaev.debug;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs one ORM summary per request, flagging N+1 queries (same SELECT repeated) and chatty
 * requests. Fed for free by the SQL listener; fixed thresholds, no config of its own.
 */
public class OrmInsightFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger("io.openaev.debug.orm");

  /** A statement repeated strictly more than this many times in one request is an N+1 suspect. */
  static final int N_PLUS_ONE_THRESHOLD = 10;

  /** A request issuing more than this many statements is flagged as chatty. */
  static final int CHATTY_THRESHOLD = 50;

  /** Cap on offending statements listed in one summary, to keep the line bounded. */
  static final int MAX_REPORTED_STATEMENTS = 5;

  private final SensitiveDataMasker masker;
  private final DebugRuntimeState runtimeState;
  private final DebugUserSource userSource;

  public OrmInsightFilter(
      SensitiveDataMasker masker, DebugRuntimeState runtimeState, DebugUserSource userSource) {
    this.masker = masker;
    this.runtimeState = runtimeState;
    this.userSource = userSource;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!runtimeState.isActive()) {
      chain.doFilter(request, response);
      return;
    }
    OrmInsightContext.start(request.getMethod() + " " + request.getRequestURI());
    try {
      chain.doFilter(request, response);
    } finally {
      report(OrmInsightContext.current());
      OrmInsightContext.clear();
    }
  }

  private void report(RequestQueryStats stats) {
    if (stats == null || stats.totalQueries() == 0) {
      return;
    }
    List<RequestQueryStats.StatementStat> repeated = stats.repeatedStatements(N_PLUS_ONE_THRESHOLD);
    boolean chatty = stats.totalQueries() > CHATTY_THRESHOLD;

    // Pre-formatted (not SLF4J placeholders) so a JSON log encoder that keeps the raw message
    // pattern still shows the resolved values, and so both branches carry user=.
    String summary =
        String.format(
            "ORM %s: %d queries (%d distinct), %dms user=%s",
            stats.requestDescription(),
            stats.totalQueries(),
            stats.distinctStatements(),
            stats.totalMillis(),
            userSource.currentUser());

    if (repeated.isEmpty() && !chatty) {
      log.info(summary);
      return;
    }

    StringBuilder sb = new StringBuilder(summary);
    if (chatty) {
      sb.append(
          String.format(
              "%n  CHATTY REQUEST: %d queries (> %d)", stats.totalQueries(), CHATTY_THRESHOLD));
    }
    int shown = 0;
    for (RequestQueryStats.StatementStat stat : repeated) {
      if (shown >= MAX_REPORTED_STATEMENTS) {
        sb.append(
            String.format(
                "%n  ... and %d more repeated statements",
                repeated.size() - MAX_REPORTED_STATEMENTS));
        break;
      }
      shown++;
      sb.append(
          String.format(
              "%n  %s: '%s' executed %dx (%dms total)",
              stat.select() ? "N+1 SUSPECTED" : "REPEATED WRITE",
              masker.maskText(stat.sql()),
              stat.count(),
              stat.totalMillis()));
    }
    log.warn(sb.toString());
  }
}
