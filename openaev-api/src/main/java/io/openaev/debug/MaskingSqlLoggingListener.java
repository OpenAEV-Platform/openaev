package io.openaev.debug;

import java.util.List;
import java.util.StringJoiner;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * datasource-proxy listener: logs each SQL statement with timing and masked parameters on {@code
 * io.openaev.debug.sql}, and feeds the per-request ORM aggregation.
 */
public class MaskingSqlLoggingListener implements QueryExecutionListener {

  private static final Logger log = LoggerFactory.getLogger("io.openaev.debug.sql");

  private final SensitiveDataMasker masker;
  private final DebugRuntimeState runtimeState;
  private final long slowQueryThresholdMillis;
  private final int maxParameterLength;

  public MaskingSqlLoggingListener(
      SensitiveDataMasker masker, DebugRuntimeState runtimeState, DebugProperties.Sql config) {
    this.masker = masker;
    this.runtimeState = runtimeState;
    this.slowQueryThresholdMillis = config.getSlowQueryThreshold().toMillis();
    this.maxParameterLength = config.getMaxParameterLength();
  }

  @Override
  public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    // Timing is read in afterQuery.
  }

  @Override
  public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
    if (!runtimeState.isActive()) {
      return; // auto-disabled
    }
    long elapsed = execInfo.getElapsedTime();

    // Feed the ORM aggregation first (counts every query, ignoring the slow-query threshold).
    for (QueryInfo queryInfo : queryInfoList) {
      OrmInsightContext.record(queryInfo.getQuery(), elapsed);
    }

    if (elapsed < slowQueryThresholdMillis || !log.isInfoEnabled()) {
      return;
    }
    for (QueryInfo queryInfo : queryInfoList) {
      String sql = masker.maskStatementText(queryInfo.getQuery());
      List<String> columns = SqlParameterColumnResolver.resolve(queryInfo.getQuery());
      String params = renderParameters(queryInfo, columns);
      log.info(
          "sql success={} time={}ms statement={} params={}",
          execInfo.isSuccess(),
          elapsed,
          sql,
          params);
    }
  }

  private String renderParameters(QueryInfo queryInfo, List<String> columns) {
    List<List<ParameterSetOperation>> parametersList = queryInfo.getParametersList();
    if (parametersList == null || parametersList.isEmpty()) {
      return "[]";
    }
    StringJoiner batches = new StringJoiner(", ", "[", "]");
    for (List<ParameterSetOperation> parameters : parametersList) {
      StringJoiner one = new StringJoiner(", ", "{", "}");
      for (ParameterSetOperation parameter : parameters) {
        Object[] args = parameter.getArgs();
        if (args == null || args.length < 2) {
          continue;
        }
        Object key = args[0];
        Object value = args[1];
        String column = columnFor(key, columns);
        String label = column != null ? column : String.valueOf(key);
        one.add(label + "=" + renderValue(column, value));
      }
      batches.add(one.toString());
    }
    return batches.toString();
  }

  private String renderValue(String column, Object value) {
    if (masker.isMaskAllParameters()) {
      return "<" + typeName(value) + ">" + masker.maskValue(column, value);
    }
    // Mask first, truncate after (truncating first could leak a prefix of a long secret).
    return truncate(masker.maskValue(column, value));
  }

  private static String typeName(Object value) {
    return value == null ? "null" : value.getClass().getSimpleName();
  }

  private String columnFor(Object key, List<String> columns) {
    if (key instanceof Integer index && index >= 1 && index <= columns.size()) {
      return columns.get(index - 1);
    }
    return null;
  }

  private String truncate(String value) {
    if (value == null || value.length() <= maxParameterLength) {
      return value;
    }
    return value.substring(0, maxParameterLength) + "...(" + value.length() + " chars)";
  }
}
