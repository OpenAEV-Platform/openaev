package io.openaev.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.core.env.Environment;

/**
 * Runtime fail-closed detector (WS1). A test-scope datasource-proxy listener that flags a statement
 * the inspector rewrote with {@code can_access_tenant} but which ran under an empty tenant scope:
 * the gate function then returns false, so the read silently returns zero rows (fail-closed in
 * production). Recorded via {@link FailClosedAccessRecorder}.
 *
 * <p>The signal is the presence of the rewrite ({@code can_access_tenant}), not the mere presence
 * of an active-table name: only a rewritten statement can fail closed, whereas raw-JDBC and ungated
 * reads of the same tables are never rewritten and never fail closed. The live scope is read from
 * the statement's own connection rather than mirrored, because the integration tests set the GUC
 * directly, bypassing the two Java setters (aspect and primitive). The table name in the report is
 * a best-effort label from {@link TenantSqlLeakOracle#mentioned(String)} over the {@code
 * openaev.tenant.active-tables} allowlist (read from the {@link Environment}); detection does not
 * depend on it.
 *
 * <p>When wired suite-wide ({@code -Dopenaev.failclosed.detector=on}, see {@link
 * FailClosedDetectorContextCustomizerFactory}) each violation is also printed with a {@code
 * [FAILCLOSED]} marker and a caller hint, so a full-suite baseline can be collected and triaged.
 */
public class FailClosedDetectorListener implements QueryExecutionListener {

  static final String MARKER = "[FAILCLOSED]";
  static final String UNKNOWN_CALLER = "unknown";
  private static final boolean SUITE_WIDE =
      "on".equals(System.getProperty("openaev.failclosed.detector"));
  private static final int SQL_HINT_LENGTH = 600;

  private static final String READ_SCOPE_SQL =
      "SELECT coalesce(current_setting('app.current_tenants', true), '')";

  private final Environment environment;
  private volatile TenantSqlLeakOracle oracle;

  public FailClosedDetectorListener(Environment environment) {
    this.environment = environment;
  }

  /**
   * The oracle over the active-tables allowlist, resolved lazily on first use: the {@link
   * Environment} (including any {@code @TestPropertySource} activation) is fully populated by the
   * time any query runs, whereas a placeholder read at bean-post-processor construction is not.
   * Cached: the allowlist is fixed for the context.
   */
  private TenantSqlLeakOracle oracle() {
    TenantSqlLeakOracle local = oracle;
    if (local == null) {
      String activeTables = environment.getProperty("openaev.tenant.active-tables", "");
      Set<String> tables =
          Arrays.stream(activeTables.split(","))
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toCollection(LinkedHashSet::new));
      local = new TenantSqlLeakOracle(tables);
      oracle = local;
    }
    return local;
  }

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // Scope is read in afterQuery, on the same connection, once the statement has run.
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    for (QueryInfo queryInfo : queryInfoList) {
      String sql = queryInfo.getQuery();
      if (sql == null || !isTenantGated(sql)) {
        continue;
      }
      if (scopeIsEmpty(execInfo)) {
        Set<String> mentioned = oracle().mentioned(sql);
        String tables = mentioned.isEmpty() ? "(gated)" : String.join(",", mentioned);
        String caller = callerHint();
        FailClosedAccessRecorder.record(tables, caller, sql);
        if (SUITE_WIDE) {
          System.out.println(MARKER + " tables=" + tables + " at=" + caller + " sql=" + hint(sql));
        }
      }
    }
  }

  /**
   * Only a statement the inspector rewrote with {@code can_access_tenant} can fail closed: that
   * function returns false under an empty scope, so the gated read returns zero rows. Raw-JDBC and
   * otherwise ungated statements are never rewritten, so an unscoped read there is not a
   * fail-closed access - which is why matching the mere presence of an active-table name
   * over-reports massively (bulk raw-JDBC seed writers, tenant-resolution bootstrap reads) while
   * this precise signal does not.
   */
  private static boolean isTenantGated(String sql) {
    // The function-call form, so a hypothetical identifier merely containing the name does not
    // match.
    return sql.toLowerCase(Locale.ROOT).contains("can_access_tenant(");
  }

  /**
   * Reads the live GUC on the statement's own connection. Reflects every setter (aspect, primitive,
   * direct test set). Fails safe: if the scope cannot be read, do not flag.
   */
  private boolean scopeIsEmpty(ExecutionInfo execInfo) {
    Statement executed = execInfo.getStatement();
    if (executed == null) {
      return false;
    }
    try {
      Connection connection = executed.getConnection();
      try (Statement statement = connection.createStatement();
          ResultSet rs = statement.executeQuery(READ_SCOPE_SQL)) {
        if (!rs.next()) {
          return true;
        }
        String scope = rs.getString(1);
        return scope == null || scope.isEmpty();
      }
    } catch (SQLException e) {
      return false;
    }
  }

  /**
   * First application frame that is not this detector, to locate the unscoped call site for triage.
   */
  private static String callerHint() {
    for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
      String className = frame.getClassName();
      if (className.startsWith("io.openaev.")
          && !className.equals(FailClosedDetectorListener.class.getName())) {
        return frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber();
      }
    }
    return UNKNOWN_CALLER;
  }

  private static String hint(String sql) {
    String collapsed = sql.replaceAll("\\s+", " ").trim();
    return collapsed.length() <= SQL_HINT_LENGTH
        ? collapsed
        : collapsed.substring(0, SQL_HINT_LENGTH) + "...";
  }
}
