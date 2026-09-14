package io.openaev.config;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

/**
 * Write-attribution detector listener, the write-side twin of {@link FailClosedDetectorListener}.
 * The detection itself is a DB trigger ({@link WriteAttrDetectorTrigger}), which sees {@code
 * NEW.tenant_id} directly and so is immune to statement shape; this listener is only the Java-side
 * reader. In {@code afterQuery} it scans the executed statement's JDBC warning chain for the
 * trigger's {@code [WRITEATTR]} marker, parses the offending table, written tenant and scope,
 * attaches the emitting Java frame, and records a signature via {@link WriteAttrDetectorRecorder}.
 *
 * <p>Reading the signal in Java (rather than in a capture table) is what lets the detector reuse
 * the fail-closed baseline-diff machinery: the recorded signature carries a {@code class.method}
 * caller, so the same reason-carrying baseline shape applies.
 *
 * <p>Known limitation: a JPA insert is flushed lazily, so the frame captured here is the flush
 * site, which for a deferred insert can be a framework or test frame rather than the create
 * handler. The detection (table, tenant, scope) is always exact; only the caller label is best
 * effort. A follow-up decides whether to key the baseline on the caller or on table plus statement
 * shape.
 */
public class WriteAttrDetectorListener implements QueryExecutionListener {

  static final String MARKER = WriteAttrDetectorTrigger.MARKER;
  static final String UNKNOWN_CALLER = "unknown";
  private static final boolean SUITE_WIDE =
      "on".equals(System.getProperty("openaev.writeattr.detector"));

  private static final Pattern SIGNAL =
      Pattern.compile("\\[WRITEATTR] table=(\\S+) tenant=(\\S+) scope=(.+)");

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // The signal is read in afterQuery, from the warnings the trigger raised while the statement
    // ran.
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    Statement statement = execInfo.getStatement();
    if (statement == null) {
      return;
    }
    try {
      SQLWarning warning = statement.getWarnings();
      if (warning == null) {
        return;
      }
      String caller = callerHint();
      for (SQLWarning w = warning; w != null; w = w.getNextWarning()) {
        String message = w.getMessage();
        if (message == null) {
          continue;
        }
        Matcher matcher = SIGNAL.matcher(message);
        if (matcher.find()) {
          String table = matcher.group(1);
          String writtenTenant = matcher.group(2);
          String scope = matcher.group(3);
          WriteAttrDetectorRecorder.record(table, writtenTenant, scope, caller);
          if (SUITE_WIDE) {
            System.out.println(
                MARKER
                    + " table="
                    + table
                    + " tenant="
                    + writtenTenant
                    + " scope="
                    + scope
                    + " at="
                    + caller);
          }
        }
      }
      // Clear so a reused prepared statement does not re-surface the same warning on a later,
      // in-scope write.
      statement.clearWarnings();
    } catch (SQLException e) {
      // Fail safe: never turn an observation error into a test failure.
    }
  }

  /**
   * First application frame that is not this detector, to locate the emitting call site. Same shape
   * as the fail-closed detector. Best effort: for a lazily flushed JPA insert this is the flush
   * site.
   */
  private static String callerHint() {
    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      String className = frame.getClassName();
      if (className.startsWith("io.openaev.")
          && !className.equals(WriteAttrDetectorListener.class.getName())) {
        return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
      }
    }
    return UNKNOWN_CALLER;
  }
}
