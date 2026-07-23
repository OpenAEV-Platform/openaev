package io.openaev.service.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.context.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * In-memory registry of massive (bulk) operations in progress, powering the header progress
 * indicator and the aggregated {@code bulk-operation} SSE events.
 *
 * <p>Massive operations no longer stream one event per mutated entity to connected browsers (see
 * {@link io.openaev.context.BulkOperationContext}); instead, this monitor publishes one {@link
 * BulkOperationEvent} at start, one per progress step (chunk), and one on completion or failure.
 * The frontend shows the progress in the top bar and refreshes its data once, when the operation
 * finishes.
 *
 * <p>The registry is node-local: operations are visible to the SSE consumers connected to the node
 * executing them, which matches the session-sticky deployment model. Finished operations are kept
 * briefly so a page refresh right after completion still shows the final state, then evicted
 * lazily.
 */
@Component
@RequiredArgsConstructor
public class BulkOperationMonitor {

  static final Duration FINISHED_RETENTION = Duration.ofMinutes(1);

  private final ApplicationEventPublisher eventPublisher;
  private final Map<String, BulkOperation> operations = new ConcurrentHashMap<>();

  /** Lifecycle status of a bulk operation. */
  public enum BulkOperationStatus {
    RUNNING,
    COMPLETED,
    FAILED
  }

  /** Immutable snapshot of a bulk operation, serialized as-is to the stream and the REST API. */
  public record BulkOperation(
      @JsonProperty("bulk_operation_id") String id,
      @JsonProperty("bulk_operation_action") String action,
      @JsonProperty("bulk_operation_entity") String entityLabel,
      @JsonProperty("bulk_operation_total") int total,
      @JsonProperty("bulk_operation_processed") int processed,
      @JsonProperty("bulk_operation_status") BulkOperationStatus status,
      @JsonProperty("bulk_operation_started_at") Instant startedAt,
      @JsonProperty("bulk_operation_finished_at") Instant finishedAt,
      @JsonIgnore String tenantId) {}

  /** Spring application event carrying a bulk operation snapshot, broadcast to the SSE stream. */
  public record BulkOperationEvent(BulkOperation operation) {}

  /** Registers a new running operation and notifies the stream. Returns the operation id. */
  public String start(String action, String entityLabel, int total) {
    evictExpired();
    String id = UUID.randomUUID().toString();
    BulkOperation operation =
        new BulkOperation(
            id,
            action,
            entityLabel,
            total,
            0,
            BulkOperationStatus.RUNNING,
            Instant.now(),
            null,
            TenantContext.getCurrentTenant());
    operations.put(id, operation);
    eventPublisher.publishEvent(new BulkOperationEvent(operation));
    return id;
  }

  /** Adds processed items to a running operation and notifies the stream. */
  public void progress(String operationId, int processedDelta) {
    BulkOperation updated =
        operations.computeIfPresent(
            operationId,
            (id, op) ->
                new BulkOperation(
                    op.id(),
                    op.action(),
                    op.entityLabel(),
                    op.total(),
                    Math.min(op.total(), op.processed() + processedDelta),
                    op.status(),
                    op.startedAt(),
                    op.finishedAt(),
                    op.tenantId()));
    if (updated != null) {
      eventPublisher.publishEvent(new BulkOperationEvent(updated));
    }
  }

  /** Marks an operation as successfully completed and notifies the stream. */
  public void complete(String operationId) {
    finish(operationId, BulkOperationStatus.COMPLETED);
  }

  /** Marks an operation as failed and notifies the stream. */
  public void fail(String operationId) {
    finish(operationId, BulkOperationStatus.FAILED);
  }

  private void finish(String operationId, BulkOperationStatus status) {
    BulkOperation updated =
        operations.computeIfPresent(
            operationId,
            (id, op) ->
                new BulkOperation(
                    op.id(),
                    op.action(),
                    op.entityLabel(),
                    op.total(),
                    // A completed operation always reports full progress, even if the caller did
                    // not emit the last progress step before completing.
                    status == BulkOperationStatus.COMPLETED ? op.total() : op.processed(),
                    status,
                    op.startedAt(),
                    Instant.now(),
                    op.tenantId()));
    if (updated != null) {
      eventPublisher.publishEvent(new BulkOperationEvent(updated));
    }
  }

  /** Returns the operations visible to the given tenant, most recent first. */
  public List<BulkOperation> findForTenant(String tenantId) {
    evictExpired();
    return operations.values().stream()
        .filter(op -> tenantId == null || Objects.equals(op.tenantId(), tenantId))
        .sorted(Comparator.comparing(BulkOperation::startedAt).reversed())
        .toList();
  }

  private void evictExpired() {
    Instant cutoff = Instant.now().minus(FINISHED_RETENTION);
    operations
        .values()
        .removeIf(
            op ->
                op.status() != BulkOperationStatus.RUNNING
                    && op.finishedAt() != null
                    && op.finishedAt().isBefore(cutoff));
  }
}
