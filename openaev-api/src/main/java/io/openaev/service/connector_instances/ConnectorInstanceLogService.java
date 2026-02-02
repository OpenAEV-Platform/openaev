package io.openaev.service.connector_instances;

import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.ConnectorInstanceLogRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
  public String transformRawLogsLineToLog(Set<String> rawLogLines) {
    return rawLogLines.stream()
        .map(line -> line.replaceAll("^,", ""))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .collect(Collectors.joining("\n"));
  }

  /**
   * Creates a new log entry for a connector instance and maintains log limit.
   *
   * @param connectorInstance the connector instance to log for
   * @param rawLog the log content to store
   * @return the saved log entry
   * @throws IllegalArgumentException if rawLog is empty
   */
  @Transactional
  public ConnectorInstanceLog pushLogByConnectorInstance(
      ConnectorInstancePersisted connectorInstance, String rawLog) throws IllegalArgumentException {
    if (rawLog.isEmpty()) {
      return null;
    }

    ConnectorInstanceLog logEntry = new ConnectorInstanceLog();
    logEntry.setConnectorInstance(connectorInstance);
    logEntry.setLog(rawLog);
    ConnectorInstanceLog saved = connectorInstanceLogRepository.save(logEntry);

    cleanupExcessLogs(connectorInstance.getId());

    return saved;
  }

  /**
   * Retrieves all logs for a specific connector instance.
   *
   * @param connectorInstanceId the connector instance identifier
   * @return list of logs for the connector instance, empty if none found
   */
  public List<ConnectorInstanceLog> findLogsByConnectorInstanceId(String connectorInstanceId) {
    return connectorInstanceLogRepository.findByConnectorInstanceId(connectorInstanceId);
  }
}
