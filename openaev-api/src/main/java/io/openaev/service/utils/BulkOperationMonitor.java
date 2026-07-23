package io.openaev.service.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.AllowRawJdbc;
import io.openaev.config.SessionHelper;
import io.openaev.context.TenantContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Registry and durable journal of massive (bulk) operations, powering the permanent header
 * indicator, its per-user history, and the aggregated {@code bulk-operation} SSE events.
 *
 * <p>Massive operations no longer stream one event per mutated entity to connected browsers (see
 * {@link io.openaev.context.BulkOperationContext}); instead, this monitor publishes one {@link
 * BulkOperationEvent} at start, one per progress step (chunk), and one on completion or failure.
 * The frontend shows the progress in the top bar and refreshes its data once, when the operation
 * finishes.
 *
 * <p>Operations are scoped to the user who launched them: the SSE events are only delivered to that
 * user's stream consumers and the history endpoint only returns the caller's own operations.
 *
 * <p>Every operation is journaled in the {@code bulk_operations} table (capped to the most recent
 * {@link #HISTORY_SIZE} per user), so the history survives restarts. The in-memory map only tracks
 * live operations for cheap snapshot updates; the journal writes are best-effort and never break
 * the underlying operation. Operations still marked RUNNING in the journal but no longer tracked in
 * memory (node restart mid-operation) are self-healed to FAILED when their last update is older
 * than {@link #STALE_RUNNING_AFTER}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@AllowRawJdbc(
    reason =
        "bulk_operations is a platform-level bookkeeping journal keyed by globally-unique operation"
            + " ids: every query explicitly scopes by the user (and tenant) columns, so bypassing"
            + " the tenant statement inspector is safe. Journal writes are best-effort and must"
            + " never break or extend the underlying massive operation.")
public class BulkOperationMonitor {

  static final Duration FINISHED_RETENTION = Duration.ofMinutes(1);
  static final int HISTORY_SIZE = 100;
  static final Duration STALE_RUNNING_AFTER = Duration.ofMinutes(5);

  private final ApplicationEventPublisher eventPublisher;
  private final JdbcTemplate jdbcTemplate;
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
      @JsonIgnore String tenantId,
      @JsonIgnore String userId) {}

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
            TenantContext.getCurrentTenant(),
            currentUserId());
    operations.put(id, operation);
    journalInsert(operation);
    eventPublisher.publishEvent(new BulkOperationEvent(operation));
    return id;
  }

  /** Adds processed items to a running operation and notifies the stream. */
  public void progress(String operationId, int processedDelta) {
    evictExpired();
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
                    op.tenantId(),
                    op.userId()));
    if (updated != null) {
      journalUpdate(updated);
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
    evictExpired();
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
                    op.tenantId(),
                    op.userId()));
    if (updated != null) {
      journalUpdate(updated);
      eventPublisher.publishEvent(new BulkOperationEvent(updated));
    }
  }

  /**
   * Returns the last {@link #HISTORY_SIZE} operations of the given user, most recent first, running
   * operations first. Live in-memory snapshots take precedence over their journal row; journal rows
   * still RUNNING but not updated for {@link #STALE_RUNNING_AFTER} (node died mid-operation:
   * progress is journaled on every chunk, so a live operation is never that quiet) are self-healed
   * to FAILED before reading.
   */
  public List<BulkOperation> findForUser(String userId, String tenantId) {
    evictExpired();
    Map<String, BulkOperation> byId = new LinkedHashMap<>();
    journalFind(userId, tenantId).forEach(op -> byId.put(op.id(), op));
    // Overlay live operations: fresher than the journal, and the only source if a journal
    // write failed.
    operations.values().stream()
        .filter(op -> userId == null || Objects.equals(op.userId(), userId))
        .filter(op -> tenantId == null || Objects.equals(op.tenantId(), tenantId))
        .forEach(op -> byId.put(op.id(), op));
    List<BulkOperation> result = new ArrayList<>(byId.values());
    result.sort(
        Comparator.comparing((BulkOperation op) -> op.status() != BulkOperationStatus.RUNNING)
            .thenComparing(BulkOperation::startedAt, Comparator.reverseOrder()));
    return result.size() > HISTORY_SIZE ? result.subList(0, HISTORY_SIZE) : result;
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

  private String currentUserId() {
    try {
      String userId = SessionHelper.currentUser().getId();
      return (userId == null || userId.isBlank()) ? null : userId;
    } catch (Exception e) {
      // System / non-request contexts have no user: the operation is journaled without one and
      // simply never appears in any user's history or stream.
      return null;
    }
  }

  // --- Journal (best-effort: never breaks the underlying operation) ---

  private void journalInsert(BulkOperation op) {
    try {
      jdbcTemplate.update(
          "INSERT INTO bulk_operations (operation_id, operation_action, operation_entity,"
              + " operation_total, operation_processed, operation_status, operation_started_at,"
              + " operation_finished_at, operation_tenant_id, operation_user_id)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          op.id(),
          op.action(),
          op.entityLabel(),
          op.total(),
          op.processed(),
          op.status().name(),
          Timestamp.from(op.startedAt()),
          op.finishedAt() == null ? null : Timestamp.from(op.finishedAt()),
          op.tenantId(),
          op.userId());
      pruneHistory(op.userId());
    } catch (Exception e) {
      log.error("Failed to journal bulk operation {}: {}", op.id(), e.getMessage(), e);
    }
  }

  private void journalUpdate(BulkOperation op) {
    try {
      jdbcTemplate.update(
          "UPDATE bulk_operations SET operation_processed = ?, operation_status = ?,"
              + " operation_finished_at = ?, operation_updated_at = now() WHERE operation_id = ?",
          op.processed(),
          op.status().name(),
          op.finishedAt() == null ? null : Timestamp.from(op.finishedAt()),
          op.id());
    } catch (Exception e) {
      log.error("Failed to update journaled bulk operation {}: {}", op.id(), e.getMessage(), e);
    }
  }

  private void pruneHistory(String userId) {
    if (userId == null) {
      return;
    }
    jdbcTemplate.update(
        "DELETE FROM bulk_operations WHERE operation_user_id = ? AND operation_id NOT IN"
            + " (SELECT operation_id FROM bulk_operations WHERE operation_user_id = ?"
            + " ORDER BY operation_started_at DESC LIMIT ?)",
        userId,
        userId,
        HISTORY_SIZE);
  }

  private List<BulkOperation> journalFind(String userId, String tenantId) {
    if (userId == null) {
      return List.of();
    }
    try {
      // Self-heal before reading: RUNNING rows not updated recently have no node processing
      // them anymore (progress is journaled on every chunk), so the node died mid-operation.
      // Live operations of this node overlay their row anyway, and their next journal write
      // restores the true status if a heal ever raced a slow-but-alive operation.
      jdbcTemplate.update(
          "UPDATE bulk_operations SET operation_status = 'FAILED', operation_finished_at = now()"
              + " WHERE operation_user_id = ? AND operation_status = 'RUNNING'"
              + " AND operation_updated_at < ?",
          userId,
          Timestamp.from(Instant.now().minus(STALE_RUNNING_AFTER)));
      RowMapper<BulkOperation> rowMapper =
          (rs, rowNum) ->
              new BulkOperation(
                  rs.getString("operation_id"),
                  rs.getString("operation_action"),
                  rs.getString("operation_entity"),
                  rs.getInt("operation_total"),
                  rs.getInt("operation_processed"),
                  BulkOperationStatus.valueOf(rs.getString("operation_status")),
                  rs.getTimestamp("operation_started_at").toInstant(),
                  rs.getTimestamp("operation_finished_at") == null
                      ? null
                      : rs.getTimestamp("operation_finished_at").toInstant(),
                  rs.getString("operation_tenant_id"),
                  rs.getString("operation_user_id"));
      if (tenantId == null) {
        return jdbcTemplate.query(
            "SELECT * FROM bulk_operations WHERE operation_user_id = ?"
                + " ORDER BY operation_started_at DESC LIMIT ?",
            rowMapper,
            userId,
            HISTORY_SIZE);
      }
      return jdbcTemplate.query(
          "SELECT * FROM bulk_operations WHERE operation_user_id = ? AND operation_tenant_id = ?"
              + " ORDER BY operation_started_at DESC LIMIT ?",
          rowMapper,
          userId,
          tenantId,
          HISTORY_SIZE);
    } catch (Exception e) {
      log.error("Failed to load bulk operation history: {}", e.getMessage(), e);
      return List.of();
    }
  }
}
