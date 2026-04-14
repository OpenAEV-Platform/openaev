package io.openaev.service.audit;

import io.openaev.config.EngineConfig;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.auditlog.EsAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Indexes audit log events into the search engine (OpenSearch / Elasticsearch) for subsequent
 * querying via the {@code /api/audit-logs/search} endpoint.
 *
 * <p>This service is only active when {@code openaev.audit-logs.opensearch.enabled=true}. It
 * receives a fully-populated {@link EsAuditLog} from {@link AuditLogService} and indexes it
 * asynchronously to avoid blocking the API response.
 */
@Service
@ConditionalOnProperty(name = "openaev.audit-logs.opensearch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AuditOpenSearchService {

  private static final String AUDIT_LOG_INDEX = "audit-log";

  private final EngineService engineService;
  private final EngineConfig engineConfig;

  /**
   * Asynchronously indexes a fully-populated audit log document into the search engine.
   *
   * @param doc the {@link EsAuditLog} already built by {@link AuditLogService}
   */
  @Async
  public void indexAuditEvent(EsAuditLog doc) {
    try {
      String index = engineConfig.getIndexPrefix() + "_" + AUDIT_LOG_INDEX;
      engineService.indexDocument(index, doc.getId(), doc);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to index audit event to search engine: {}", e.getMessage(), e);
    }
  }
}
