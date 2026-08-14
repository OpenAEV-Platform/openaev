package io.openaev.service.connector_instances;

import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.ConnectorInstanceLogRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInstanceLogService {
  public static final long LOG_SIZE_LIMIT = 10L;
  private final ConnectorInstanceLogRepository connectorInstanceLogRepository;

  private void cleanupExcessLogs(String connectorInstanceId) {
    long currentCount =
        connectorInstanceLogRepository.countByConnectorInstanceId(connectorInstanceId);

    if (currentCount > LOG_SIZE_LIMIT) {
      long excessCount = (currentCount - LOG_SIZE_LIMIT);
      connectorInstanceLogRepository.deleteOldestLogByConnectorInstanceId(
          connectorInstanceId, excessCount);
      log.info("Deleted {} old logs for instance {}", excessCount, connectorInstanceId);
    }
  }

  /**
   * Transforms raw log lines into a single formatted log string.
   *
   * @param rawLogLines the set of raw log lines to transform
   * @return the formatted log string with lines separated by newlines
   */
  public Set<String> transformRawLogsLineToLog(Set<String> rawLogLines) {
    return rawLogLines.stream()
        .map(line -> line.replaceAll("^,", ""))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(Collectors.toSet());
  }

  /**
   * Creates a new log entry for a connector instance and maintains log limit.
   *
   * @param connectorInstance the connector instance to log for
   * @param rawLogs the log content to store
   * @throws IllegalArgumentException if rawLog is empty
   */
  @Transactional
  public void pushLogByConnectorInstance(
      ConnectorInstancePersisted connectorInstance, Set<String> rawLogs)
      throws IllegalArgumentException {
    if (rawLogs.isEmpty()) {
      return;
    }
    for (String log : rawLogs) {
      ConnectorInstanceLog logEntry = new ConnectorInstanceLog();
      logEntry.setConnectorInstance(connectorInstance);
      logEntry.setLog(log);
      connectorInstanceLogRepository.save(logEntry);
    }

    cleanupExcessLogs(connectorInstance.getId());
  }

  /**
   * Searches logs for a specific connector instance with pagination.
   *
   * @param connectorInstanceId the connector instance identifier
   * @param input the search pagination input
   * @return a page of logs for the connector instance
   */
  @Transactional(readOnly = true)
  public Page<ConnectorInstanceLog> searchLogsByConnectorInstanceId(
      String connectorInstanceId, SearchPaginationInput input) {
    return connectorInstanceLogRepository.searchByConnectorInstanceId(
        connectorInstanceId, PageRequest.of(input.getPage(), input.getSize()));
  }
}
