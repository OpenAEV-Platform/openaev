package io.openaev.service.connector_instances;

import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.ConnectorInstanceLogRepository;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConnectorInstanceLogService {
  /** Matches the tail XTM Composer sends on each push (the last 100 container log lines). */
  public static final int LOG_SIZE_LIMIT = 100;

  private final ConnectorInstanceLogRepository connectorInstanceLogRepository;
  private final ConnectorInstanceService connectorInstanceService;

  /**
   * Cleans raw container log lines, keeping their order: strips the leading separator comma, trims,
   * drops blank lines, and keeps only the last {@link #LOG_SIZE_LIMIT} lines.
   *
   * @param rawLogLines the raw log lines, oldest first
   * @return the cleaned log lines, oldest first
   */
  public List<String> transformRawLogsLineToLog(List<String> rawLogLines) {
    List<String> lines =
        rawLogLines.stream()
            .map(line -> line.replaceAll("^,", ""))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .toList();
    return lines.subList(Math.max(0, lines.size() - LOG_SIZE_LIMIT), lines.size());
  }

  /**
   * Replaces the stored logs of a connector instance with the latest snapshot.
   *
   * <p>XTM Composer pushes the container's last lines on every report, so each push is a full
   * snapshot rather than new lines: appending it piled up duplicates and the old 10-row cap kept an
   * arbitrary subset. The snapshot replaces the stored logs, as OpenCTI does with the same
   * composer. Each line gets a strictly increasing timestamp so the order survives the newest-first
   * search. An identical snapshot (frequent between two collector runs) is not rewritten.
   *
   * @param connectorInstance the connector instance the logs belong to
   * @param logLines the cleaned log lines, oldest first
   */
  @Transactional
  public void pushLogByConnectorInstance(
      ConnectorInstancePersisted connectorInstance, List<String> logLines) {
    if (logLines.isEmpty()) {
      return;
    }
    List<String> storedLines =
        connectorInstanceLogRepository
            .findOrderedByConnectorInstanceId(connectorInstance.getId())
            .stream()
            .map(ConnectorInstanceLog::getLog)
            .toList();
    if (storedLines.equals(logLines)) {
      return;
    }
    connectorInstanceLogRepository.deleteByConnectorInstanceId(connectorInstance.getId());

    Instant firstLineAt = Instant.now();
    List<ConnectorInstanceLog> snapshot = new ArrayList<>(logLines.size());
    for (int i = 0; i < logLines.size(); i++) {
      ConnectorInstanceLog logEntry = new ConnectorInstanceLog();
      logEntry.setConnectorInstance(connectorInstance);
      logEntry.setLog(logLines.get(i));
      // Microsecond steps: PostgreSQL timestamps keep microseconds, not nanoseconds.
      logEntry.setConnector_instance_log_created_at(firstLineAt.plus(i, ChronoUnit.MICROS));
      snapshot.add(logEntry);
    }
    connectorInstanceLogRepository.saveAll(snapshot);
  }

  /**
   * Searches logs for a specific connector instance with pagination.
   *
   * <p>Validates the connector instance is visible in the caller's tenant scope first: the logs
   * table has no {@code tenant_id} of its own and rides along with its parent connector instance,
   * so searching by {@code connectorInstanceId} alone (as before) let any tenant read any other
   * tenant's logs. {@link ConnectorInstanceService#connectorInstanceById} enforces the same
   * ownership check used by every other read on this table (v1 {@code @Filter} today, the v2
   * inspector once {@code connector_instances} is activated).
   *
   * @param connectorInstanceId the connector instance identifier
   * @param input the search pagination input
   * @return a page of logs for the connector instance
   * @throws EntityNotFoundException if the connector instance is not visible in the current scope
   */
  @Transactional(readOnly = true)
  public Page<ConnectorInstanceLog> searchLogsByConnectorInstanceId(
      String connectorInstanceId, SearchPaginationInput input) {
    connectorInstanceService.connectorInstanceById(connectorInstanceId);
    return connectorInstanceLogRepository.searchByConnectorInstanceId(
        connectorInstanceId, PageRequest.of(input.getPage(), input.getSize()));
  }
}
