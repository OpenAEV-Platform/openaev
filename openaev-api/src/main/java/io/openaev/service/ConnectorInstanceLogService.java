package io.openaev.service;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.repository.ConnectorInstanceLogRepository;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInstanceLogService {
  @Getter
  private final Integer LOG_SIZE_LIMIT=5;
  private final ConnectorInstanceLogRepository connectorInstanceLogRepository;

  public String transformRawLogsLineToLog(Set<String> rawLogLines) {
    return rawLogLines.stream()
            .map(line -> line.replaceAll("^,", ""))  // Remove leading commas
            .map(String::trim)                 // Trim whitespace
            .filter(line -> !line.isEmpty())          // Remove empty lines
            .collect(Collectors.joining("\n"));
  }

  private void cleanupExcessLogs(String connectorInstanceId) {
    long currentCount = connectorInstanceLogRepository.countByConnectorInstanceId(connectorInstanceId);

    if (currentCount > LOG_SIZE_LIMIT) {
      int excessCount = (int) (currentCount - LOG_SIZE_LIMIT);
      connectorInstanceLogRepository.deleteOldestLogByConnectorInstanceId(connectorInstanceId, excessCount);
      log.info("Deleted {} old logs for instance {}", excessCount, connectorInstanceId);
    }
  }

  @Transactional
  public ConnectorInstanceLog pushLogByConnectorInstance(ConnectorInstance connectorInstance, String rawLog) {
    if (rawLog.isEmpty()) {
      throw new IllegalArgumentException("Instance ID and log cannot be empty");
    }

    ConnectorInstanceLog logEntry = new ConnectorInstanceLog();
    logEntry.setConnectorInstance(connectorInstance);
    logEntry.setLog(rawLog);
    ConnectorInstanceLog saved = connectorInstanceLogRepository.save(logEntry);

    cleanupExcessLogs(connectorInstance.getId());

    return saved;
  }
}
