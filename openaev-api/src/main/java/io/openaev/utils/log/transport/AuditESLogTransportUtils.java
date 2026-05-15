package io.openaev.utils.log.transport;

import io.openaev.config.EngineConfig;
import io.openaev.engine.EngineService;
import io.openaev.engine.model.log.LogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Indexes audit log events into the search engine (OpenSearch / Elasticsearch) for subsequent
 * querying via the {@code /api/audit-logs/search} endpoint.
 *
 * <p>This service is only active when {@code openaev.audit-logs.opensearch.enabled=true}. It
 * receives a fully-populated {@link LogEvent} and indexes it
 * asynchronously to avoid blocking the API response.
 */
@Component
@ConditionalOnProperty(name = "openaev.audit-logs.opensearch.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AuditESLogTransportUtils implements AuditLogTransportUtils {

    @Value("${openaev.audit-logs.opensearch.enabled:false}")
    private boolean enabled;

    private static final String AUDIT_LOG_INDEX = "audit-log";

    private final EngineService engineService;
    private final EngineConfig engineConfig;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Asynchronously indexes a fully-populated audit log document into the search engine.
     *
     * @param doc the {@link LogEvent}
     */
    @Async
    public CompletableFuture<Boolean> send(LogEvent doc, Object level) {
        try {
            String index = engineConfig.getIndexPrefix() + "_" + AUDIT_LOG_INDEX;
            engineService.indexDocument(index, doc.getId(), doc);

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to index audit event to search engine: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(false);
    }

    @Async
    public CompletableFuture<Boolean> send(String message, Object level) {
        try {
            String index = engineConfig.getIndexPrefix() + "_" + AUDIT_LOG_INDEX;
            String uuid = UUID.nameUUIDFromBytes(message.getBytes(StandardCharsets.UTF_8)).toString();
            engineService.indexDocument(index, uuid, message);

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to index audit event to search engine: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(false);
    }
}