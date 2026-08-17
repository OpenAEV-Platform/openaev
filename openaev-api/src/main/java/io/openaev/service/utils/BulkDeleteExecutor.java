package io.openaev.service.utils;

import io.openaev.context.BulkOperationContext;
import io.openaev.context.TxCtx;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executes bulk deletions in small, independent transactions with a transparent deadlock retry.
 *
 * <p>Deleting a large batch of heavily-connected entities in a single transaction proved harmful in
 * production: the transaction holds row locks on hot tables for its whole duration (a scenario
 * deletion flushes {@code update exercises ...} rows via the {@code @PreRemove} reference-nulling
 * hook, while agents concurrently update the same rows through inject expectation results), which
 * both deadlocks against those writers and trips Hikari's 30s connection-leak detection. Chunking
 * keeps every transaction short, and when PostgreSQL still elects one of our chunks as a deadlock
 * victim, the chunk is simply retried - the concurrent writer has committed by then.
 *
 * <p>Each deletion is also tracked as a massive operation: per-entity SSE events are suppressed
 * while chunks run (see {@link BulkOperationContext}) and connected browsers receive aggregated
 * {@code bulk-operation} progress events through the {@link BulkOperationMonitor} instead, so a
 * large deletion no longer forces every open screen to refresh once per deleted entity.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BulkDeleteExecutor {

  /** Small chunks keep each transaction - and thus the row locks it holds - short. */
  static final int CHUNK_SIZE = 25;

  static final int MAX_ATTEMPTS = 3;
  private static final long RETRY_BASE_DELAY_MS = 250;

  private final BulkDeleteChunkRunner chunkRunner;
  private final BulkOperationMonitor bulkOperationMonitor;
  private final EntityManager entityManager;

  /**
   * Resolves the deletion scope (typically a specification-based id lookup) inside a short
   * transaction, so the Hibernate tenant filter applies to the query.
   */
  public <T> T resolveInTransaction(Supplier<T> resolver) {
    return chunkRunner.call(resolver);
  }

  /**
   * Same as {@link #resolveInTransaction(Supplier)} with an explicit tenant scope for the short
   * read transaction (needed when the chunk touches tenant-active tables).
   */
  public <T> T resolveInTransaction(TxCtx ctx, Supplier<T> resolver) {
    return chunkRunner.call(ctx, resolver);
  }

  /**
   * Deletes the given ids chunk by chunk, each chunk in its own transaction, retrying chunks that
   * lose a deadlock against a concurrent writer.
   *
   * <p>Chunks that already committed stay deleted if a later chunk ultimately fails: bulk deletion
   * is not atomic across chunks (re-running the same request completes the remainder). Callers must
   * NOT be transactional - inside an active transaction (e.g. transactional integration tests)
   * chunked commits are impossible, so the whole deletion runs inline in the caller's transaction,
   * without retry.
   *
   * @param entityLabel human label for logs (e.g. "scenarios")
   * @param ids ids to delete, in any order
   * @param chunkDeleter performs the actual deletion of one chunk of ids
   * @return the ids submitted for deletion, sorted
   */
  public List<String> deleteInChunks(
      String entityLabel, List<String> ids, Consumer<List<String>> chunkDeleter) {
    return deleteInChunks(null, entityLabel, ids, chunkDeleter);
  }

  /**
   * Same as {@link #deleteInChunks(String, List, Consumer)} with an explicit tenant scope on each
   * chunk transaction, so tenant-active tables are visible to the deleter.
   */
  public List<String> deleteInChunks(
      TxCtx ctx, String entityLabel, List<String> ids, Consumer<List<String>> chunkDeleter) {
    if (ids.isEmpty()) {
      return List.of();
    }
    // Deterministic deletion order: two concurrent bulk deletions with overlapping scopes acquire
    // their row locks in the same order instead of deadlocking each other.
    List<String> sortedIds = ids.stream().sorted().toList();
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      chunkDeleter.accept(sortedIds);
      return sortedIds;
    }
    String operationId = bulkOperationMonitor.start("delete", entityLabel, sortedIds.size());
    try {
      for (int start = 0; start < sortedIds.size(); start += CHUNK_SIZE) {
        List<String> chunk =
            sortedIds.subList(start, Math.min(start + CHUNK_SIZE, sortedIds.size()));
        deleteChunkWithRetry(ctx, entityLabel, chunk, chunkDeleter);
        // With open-session-in-view the request session outlives each chunk transaction: clear it
        // so deleted entities (and the related entities touched by @PreRemove hooks) do not
        // accumulate in the persistence context across a large bulk.
        entityManager.clear();
        bulkOperationMonitor.progress(operationId, chunk.size());
      }
      bulkOperationMonitor.complete(operationId);
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
    return sortedIds;
  }

  private void deleteChunkWithRetry(
      TxCtx ctx, String entityLabel, List<String> chunk, Consumer<List<String>> chunkDeleter) {
    for (int attempt = 1; ; attempt++) {
      try {
        // The suppression scope wraps the transaction proxy call (not just the work): lifecycle
        // events also fire during the commit-time flush, which happens inside chunkRunner.call
        // after the work returns.
        BulkOperationContext.runSuppressed(
            () -> {
              Supplier<Void> work =
                  () -> {
                    chunkDeleter.accept(chunk);
                    return null;
                  };
              if (ctx != null) {
                return chunkRunner.call(ctx, work);
              }
              return chunkRunner.call(work);
            });
        return;
      } catch (ConcurrencyFailureException e) {
        if (attempt >= MAX_ATTEMPTS) {
          log.error(
              "Bulk delete of {} {}: still conflicting with concurrent writers after {} attempts, giving up",
              chunk.size(),
              entityLabel,
              attempt);
          throw e;
        }
        log.warn(
            "Bulk delete of {} {}: lost a lock conflict against a concurrent writer (attempt {}/{}), retrying",
            chunk.size(),
            entityLabel,
            attempt,
            MAX_ATTEMPTS);
        sleepBeforeRetry(attempt, e);
      }
    }
  }

  private void sleepBeforeRetry(int attempt, ConcurrencyFailureException cause) {
    long delay =
        RETRY_BASE_DELAY_MS * attempt + ThreadLocalRandom.current().nextLong(RETRY_BASE_DELAY_MS);
    try {
      Thread.sleep(delay);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw cause;
    }
  }
}
