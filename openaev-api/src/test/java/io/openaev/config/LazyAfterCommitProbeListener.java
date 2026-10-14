package io.openaev.config;

import io.openaev.config.LazyAfterCommitRecorder.Incident;
import io.openaev.config.LazyAfterCommitRecorder.Phase;
import io.openaev.config.LazyCollectionInitTracker.Initialization;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Finds the reads of tenant-active tables that an HTTP request issues outside any declared
 * transaction, and measures what each one loses.
 *
 * <p>The tenant scope is transaction-local, so it is gone once the request's {@code @Transactional}
 * call returns, while {@code open-in-view} keeps the session open for Jackson. A lazy association
 * initialised during serialisation therefore reads with no scope, and {@code can_access_tenant}
 * filters every row out: the response carries an empty collection rather than an error.
 *
 * <p>What makes a read suspect is decided the way production runs it, not the way the test does: a
 * statement rewritten with {@code can_access_tenant}, issued while a request is being served, with
 * no transaction interceptor or template on the stack. Most integration tests wrap the request in
 * their own transaction, which keeps the scope alive during serialisation and hides the loss, so
 * the probe does not rely on the scope it observes. It replays the statement with the same
 * parameters twice, under the request's scope and under an empty one, and records both counts: the
 * empty-scope count is what production returns.
 *
 * <p>Reuses the datasource-proxy seam of {@link FailClosedDetectorListener}; the fail-closed
 * detector catches a scoped transaction reading with no scope, this one the reads that happen after
 * the transaction is over.
 */
public class LazyAfterCommitProbeListener implements QueryExecutionListener {

  static final String MARKER = "[LAZYAFTERCOMMIT]";
  static final String OUTPUT_PROPERTY = "openaev.lazyprobe.out";
  static final String REQUEST_SCOPE_ATTRIBUTE =
      LazyAfterCommitProbeListener.class.getName() + ".requestScope";

  private static final String READ_SCOPE_SQL =
      "SELECT coalesce(current_setting('app.current_tenants', true), '')";
  private static final String SET_SCOPE_SQL = "SELECT set_config('app.current_tenants', ?, true)";
  private static final Pattern SCOPE_SETTER =
      Pattern.compile(
          "set_config\\(\\s*'app\\.current_tenants'\\s*,\\s*\\?", Pattern.CASE_INSENSITIVE);
  private static final Pattern SELECT =
      Pattern.compile("^\\s*(select|with)\\b", Pattern.CASE_INSENSITIVE);

  /** Frames that mean a declared transaction is open around the read. */
  private static final Set<String> TRANSACTION_FRAMES =
      Set.of(
          "org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction",
          "org.springframework.transaction.support.TransactionTemplate.execute");

  /** The frame of a running dispatch: a request bound to the thread after it ended is not one. */
  private static final String DISPATCH_FRAME =
      "org.springframework.web.servlet.DispatcherServlet.doDispatch";

  private static final String RESPONSE_WRITE_FRAME =
      "org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodProcessor"
          + ".writeWithMessageConverters";

  /** Set while the probe replays a statement, so it never inspects its own replay. */
  private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

  private final Environment environment;
  private final Supplier<DataSource> rawDataSource;
  private volatile TenantSqlLeakOracle oracle;

  public LazyAfterCommitProbeListener(Environment environment, Supplier<DataSource> rawDataSource) {
    this.environment = environment;
    this.rawDataSource = rawDataSource;
  }

  /** Remembers the scope the request's transactional call set, to replay against it later. */
  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return;
    }
    for (QueryInfo query : queryInfoList) {
      if (query.getQuery() == null || !SCOPE_SETTER.matcher(query.getQuery()).find()) {
        continue;
      }
      String scope = boundString(query);
      if (scope != null && !scope.isEmpty()) {
        request.setAttribute(REQUEST_SCOPE_ATTRIBUTE, scope);
      }
    }
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    if (REPLAYING.get()) {
      return;
    }
    for (QueryInfo query : queryInfoList) {
      String sql = query.getQuery();
      if (sql == null || !isGatedRead(sql)) {
        continue;
      }
      HttpServletRequest request = currentRequest();
      if (request == null) {
        continue;
      }
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      if (!hasFrame(stack, DISPATCH_FRAME) || insideDeclaredTransaction(stack)) {
        continue;
      }
      recordIncident(execInfo, query, sql, request, stack);
    }
  }

  private void recordIncident(
      ExecutionInfo execInfo,
      QueryInfo query,
      String sql,
      HttpServletRequest request,
      StackTraceElement[] stack) {
    boolean transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
    Object recorded = request.getAttribute(REQUEST_SCOPE_ATTRIBUTE);
    String requestScope = recorded == null ? "" : recorded.toString();
    Connection connection = connectionOf(execInfo);
    String scopeAtRead = connection == null ? "" : readScope(connection);
    int[] counts;
    REPLAYING.set(true);
    try {
      counts =
          transactionActive
              ? replayInOpenTransaction(connection, sql, query, requestScope, scopeAtRead)
              : replayOnOwnConnection(sql, query, requestScope);
    } finally {
      REPLAYING.set(false);
    }
    Initialization initialization = LazyCollectionInitTracker.current();
    Incident incident =
        new Incident(
            request.getMethod() + " " + routePattern(request),
            handlerName(request),
            phaseOf(stack),
            initialization == null ? null : initialization.role(),
            initialization == null ? null : initialization.ownerId(),
            String.join(",", oracle().mentioned(sql)),
            transactionActive,
            scopeAtRead,
            requestScope,
            counts[0],
            counts[1],
            callerHint(stack),
            sql);
    LazyAfterCommitRecorder.record(incident, System.getProperty(OUTPUT_PROPERTY));
    if ("on"
        .equals(
            System.getProperty(LazyAfterCommitProbeContextCustomizerFactory.ENABLED_PROPERTY))) {
      System.out.println(MARKER + " " + incident.toTsv());
    }
  }

  private static boolean isGatedRead(String sql) {
    return SELECT.matcher(sql).find()
        && sql.toLowerCase(Locale.ROOT).contains("can_access_tenant(");
  }

  /** The request being served on this thread, only once a handler has been matched for it. */
  private static HttpServletRequest currentRequest() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return null;
    }
    HttpServletRequest request = servletAttributes.getRequest();
    return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) == null
        ? null
        : request;
  }

  private static boolean insideDeclaredTransaction(StackTraceElement[] stack) {
    return Arrays.stream(stack)
        .anyMatch(
            frame ->
                TRANSACTION_FRAMES.contains(frame.getClassName() + "." + frame.getMethodName()));
  }

  private static Phase phaseOf(StackTraceElement[] stack) {
    return hasFrame(stack, RESPONSE_WRITE_FRAME) ? Phase.SERIALIZATION : Phase.HANDLER;
  }

  private static boolean hasFrame(StackTraceElement[] stack, String qualifiedMethod) {
    return Arrays.stream(stack)
        .anyMatch(
            frame -> qualifiedMethod.equals(frame.getClassName() + "." + frame.getMethodName()));
  }

  private static String routePattern(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    return pattern == null ? request.getRequestURI() : pattern.toString();
  }

  private static String handlerName(HttpServletRequest request) {
    Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
    if (handler instanceof HandlerMethod method) {
      return method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
    }
    return String.valueOf(handler);
  }

  /**
   * Replays a read made inside a test transaction on that same connection, the only one that sees
   * the test's uncommitted rows.
   *
   * <p>Inside a savepoint, released on every path. A failing replay statement would otherwise leave
   * the surrounding transaction aborted and the scope on whichever value the probe set last, so the
   * instrument would change what the test measures next. Rolling back to the savepoint undoes both,
   * since the scope is transaction-local and savepoint-aware.
   */
  private static int[] replayInOpenTransaction(
      Connection connection, String sql, QueryInfo query, String requestScope, String scopeAtRead) {
    if (connection == null) {
      return new int[] {-1, -1};
    }
    try {
      Savepoint savepoint = connection.setSavepoint("lazy_after_commit_probe");
      try {
        setScope(connection, requestScope.isEmpty() ? scopeAtRead : requestScope);
        int underRequestScope = countRows(connection, sql, query);
        setScope(connection, "");
        int underEmptyScope = countRows(connection, sql, query);
        return new int[] {underRequestScope, underEmptyScope};
      } finally {
        connection.rollback(savepoint);
        connection.releaseSavepoint(savepoint);
        setScope(connection, scopeAtRead);
      }
    } catch (SQLException e) {
      return new int[] {-1, -1};
    }
  }

  /**
   * Replays a read made after the commit on a connection of its own, in a throwaway transaction.
   */
  private int[] replayOnOwnConnection(String sql, QueryInfo query, String requestScope) {
    DataSource dataSource = rawDataSource.get();
    if (dataSource == null) {
      return new int[] {-1, -1};
    }
    try (Connection connection = dataSource.getConnection()) {
      boolean autoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);
      try {
        setScope(connection, requestScope);
        int underRequestScope = countRows(connection, sql, query);
        setScope(connection, "");
        int underEmptyScope = countRows(connection, sql, query);
        return new int[] {underRequestScope, underEmptyScope};
      } finally {
        connection.rollback();
        connection.setAutoCommit(autoCommit);
      }
    } catch (SQLException e) {
      return new int[] {-1, -1};
    }
  }

  private static void setScope(Connection connection, String scope) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(SET_SCOPE_SQL)) {
      statement.setString(1, scope);
      statement.execute();
    }
  }

  private static int countRows(Connection connection, String sql, QueryInfo query)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      bind(statement, query);
      try (ResultSet rows = statement.executeQuery()) {
        int count = 0;
        while (rows.next()) {
          count++;
        }
        return count;
      }
    }
  }

  /** Re-applies the parameter calls datasource-proxy captured on the original statement. */
  private static void bind(PreparedStatement statement, QueryInfo query) throws SQLException {
    if (query.getParametersList().isEmpty()) {
      return;
    }
    for (ParameterSetOperation operation : query.getParametersList().getFirst()) {
      try {
        operation.getMethod().invoke(statement, operation.getArgs());
      } catch (ReflectiveOperationException e) {
        throw new SQLException("cannot re-bind " + operation.getMethod().getName(), e);
      }
    }
  }

  private static String boundString(QueryInfo query) {
    if (query.getParametersList().isEmpty()) {
      return null;
    }
    for (ParameterSetOperation operation : query.getParametersList().getFirst()) {
      Object[] args = operation.getArgs();
      if (args != null && args.length >= 2 && args[1] instanceof String value) {
        return value;
      }
    }
    return null;
  }

  private static Connection connectionOf(ExecutionInfo execInfo) {
    Statement statement = execInfo.getStatement();
    if (statement == null) {
      return null;
    }
    try {
      return statement.getConnection();
    } catch (SQLException e) {
      return null;
    }
  }

  private static String readScope(Connection connection) {
    try (Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(READ_SCOPE_SQL)) {
      return rows.next() && rows.getString(1) != null ? rows.getString(1) : "";
    } catch (SQLException e) {
      return "";
    }
  }

  /** First application frame that is not part of the probe, for triage. */
  private static String callerHint(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      String className = frame.getClassName();
      if (className.startsWith("io.openaev.") && !className.startsWith("io.openaev.config.Lazy")) {
        return className + "." + frame.getMethodName() + ":" + frame.getLineNumber();
      }
    }
    return FailClosedDetectorListener.UNKNOWN_CALLER;
  }

  private TenantSqlLeakOracle oracle() {
    TenantSqlLeakOracle local = oracle;
    if (local == null) {
      Set<String> tables =
          Arrays.stream(environment.getProperty("openaev.tenant.active-tables", "").split(","))
              .map(String::trim)
              .filter(name -> !name.isEmpty())
              .collect(Collectors.toCollection(LinkedHashSet::new));
      local = new TenantSqlLeakOracle(tables);
      oracle = local;
    }
    return local;
  }
}
