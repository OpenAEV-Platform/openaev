package io.openaev.utils.log.transport;

import io.openaev.config.AuditLogProperties;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.LogTransport;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.log.LogEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Indexes audit log events into the search engine (OpenSearch / Elasticsearch) for subsequent
 * querying via the {@code /api/audit-logs/search} endpoint.
 *
 * <p>This transport is active when {@code engine} is listed in {@code
 * openaev.audit-logs.transports}.
 */
@Component
@Order(3) // Ensure this runs after console transport (priority 1) and file transport (priority 2)
@RequiredArgsConstructor
@Slf4j
public class AuditEngineLogTransport implements AuditLogTransportUtils {

  private final AuditLogProperties auditLogProperties;

  @Override
  public boolean isEnabled() {
    return auditLogProperties.isTransportEnabled(LogTransport.ENGINE);
  }

  private static final String AUDIT_LOG_INDEX = "audit-log";

  private final EngineService engineService;
  private final EngineConfig engineConfig;

  /** Indexes a fully-populated audit log document into the search engine. */
  @Override
  public boolean send(LogEvent doc, Object level) {
    try {
      String index = engineConfig.getIndexPrefix() + "_" + AUDIT_LOG_INDEX;
      engineService.indexDocument(index, doc.getId(), doc);
      return true;
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to index audit event to search engine: {}", e.getMessage(), e);
      return false;
    }
  }

  /** Indexes a plain text audit message into the search engine. */
  @Override
  public boolean send(String message, Object level) {
    try {
      String index = engineConfig.getIndexPrefix() + "_" + AUDIT_LOG_INDEX;
      String uuid = UUID.randomUUID().toString();
      engineService.indexDocument(index, uuid, message);
      return true;
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to index audit message to search engine: {}", e.getMessage(), e);
      return false;
    }
  }
}
