package io.openaev.service;

import io.openaev.database.model.IndexingStatus;
import io.openaev.engine.model.EsBase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Shared helpers for the incremental indexing loop of both engine implementations (Elasticsearch
 * and OpenSearch): cursor advancement that is safe at LIMIT boundaries, classification of
 * deterministic (poison) bulk item errors, and the index reset protocol (see {@link
 * #REINDEX_REQUESTED_CURSOR}).
 */
public final class EsIndexingUtils {

  /**
   * Bulk item error types that are deterministic for a given document: retrying the exact same
   * document can never succeed, so keeping the cursor would block the model's indexing forever.
   */
  private static final Set<String> POISON_ERROR_TYPES =
      Set.of(
          "strict_dynamic_mapping_exception",
          "mapper_parsing_exception",
          "document_parsing_exception",
          "illegal_argument_exception");

  /**
   * Sentinel cursor that requests a full reset of a model's index at the next startup: the engine
   * drivers wipe and recreate the index, then re-feed it from epoch (see {@link
   * #isReindexRequested(String, Optional)}).
   *
   * <p>Deleting the {@code indexing_status} row also requests a reset, but only reliably when no
   * other instance is running: the incremental indexer of a pod still up during a rolling deploy
   * (or a peer replica) treats a missing row as "index from epoch" and re-creates it within one
   * sync period, so the new pod finds a row at boot and never wipes the index. The 3.260922
   * expectation repair migration deleted rows this way and left the documents of the deleted rows
   * in the engine as permanent "pending" ghosts. A far-future cursor cannot be clobbered that way:
   * a pod running any version fetches "rows updated after year 9999", gets nothing, reports the
   * model as up to date and leaves the row untouched.
   *
   * <p>The one write that can still replace the sentinel is a sync round that read the row BEFORE
   * the request landed and persists its advanced cursor at the end of the round. Pods running this
   * version never do so: the cursor is persisted through {@code
   * IndexingStatusRepository#advanceCursorUnlessResetRequested}, a conditional upsert that leaves a
   * cursor at or beyond {@link #REINDEX_REQUESTED_THRESHOLD} untouched. Pods running an older
   * version do persist unconditionally, which is why a migration requesting a reset also registers
   * it in-process ({@link #requestReindexAtStartup}): the pod that applies the migration performs
   * the reset whatever an older pod did to the row meanwhile.
   */
  public static final Instant REINDEX_REQUESTED_CURSOR = Instant.parse("9999-12-31T00:00:00Z");

  /**
   * Lower bound of the reset request range: any cursor at or beyond it is a reset request. A
   * legitimate cursor never exceeds wall-clock (see {@link #capCursorToGraceWindow}), so the range
   * is unambiguous, and the margin under {@link #REINDEX_REQUESTED_CURSOR} makes the predicate
   * tolerant to a shifted round trip of the stored value (a time-zone conversion of a few hours, a
   * date written without its time part): a request must never turn into a regular cursor and lose
   * the reset silently.
   */
  public static final Instant REINDEX_REQUESTED_THRESHOLD = Instant.parse("9000-01-01T00:00:00Z");

  /**
   * Models whose index reset was requested in-process, by a Flyway migration applied during the
   * startup in progress (see {@link #requestReindexAtStartup}).
   */
  private static final Set<String> STARTUP_RESET_REQUESTS = ConcurrentHashMap.newKeySet();

  private EsIndexingUtils() {}

  /**
   * Returns true when the bulk item error type identifies a deterministic document-level failure
   * (mapping/parsing) that would fail identically on every retry.
   */
  public static boolean isPoisonError(String errorType) {
    return errorType != null && POISON_ERROR_TYPES.contains(errorType);
  }

  /**
   * Whether a model's {@code indexing_status} row requests a reset of its index: no row (never
   * initialized, or reset requested by deleting the row) or a row carrying a cursor at or beyond
   * {@link #REINDEX_REQUESTED_THRESHOLD} (the {@link #REINDEX_REQUESTED_CURSOR} sentinel, written
   * while other instances may still run).
   *
   * @param status the model's indexing status row, when present
   * @return true when the row requests a reset
   */
  public static boolean isReindexRequested(Optional<IndexingStatus> status) {
    if (status.isEmpty()) {
      return true;
    }
    Instant cursor = status.get().getLastIndexing();
    return cursor != null && !cursor.isBefore(REINDEX_REQUESTED_THRESHOLD);
  }

  /**
   * Whether a model's index must be wiped and rebuilt at startup: its {@code indexing_status} row
   * requests it ({@link #isReindexRequested(Optional)}) or a migration applied during this very
   * startup requested it in-process ({@link #requestReindexAtStartup}).
   *
   * @param modelName the engine model name (the {@code indexing_status_type})
   * @param status the model's indexing status row, when present
   * @return true when the index has to be reset before indexing resumes
   */
  public static boolean isReindexRequested(String modelName, Optional<IndexingStatus> status) {
    return STARTUP_RESET_REQUESTS.contains(modelName) || isReindexRequested(status);
  }

  /**
   * Registers, in this process, a reset of a model's index to be performed by the startup in
   * progress. Meant for a Flyway Java migration that also writes the {@link
   * #REINDEX_REQUESTED_CURSOR} sentinel: Flyway runs before the engine drivers' boot check, so the
   * pod that applies the migration wipes and recreates the index even when a pod still running a
   * version older than this one overwrote the sentinel with the cursor of a sync round that was in
   * flight when the migration committed. The registration is cleared by {@link
   * #reindexRequestFulfilled} once the index has actually been wiped and recreated; a process that
   * dies before that point simply leaves the row-level request in place for the next boot.
   *
   * @param modelName the engine model name (the {@code indexing_status_type})
   */
  public static void requestReindexAtStartup(String modelName) {
    STARTUP_RESET_REQUESTS.add(modelName);
  }

  /**
   * Clears the in-process reset request of a model once its index has been wiped and recreated.
   *
   * @param modelName the engine model name (the {@code indexing_status_type})
   */
  public static void reindexRequestFulfilled(String modelName) {
    STARTUP_RESET_REQUESTS.remove(modelName);
  }

  /**
   * Message of the startup failure raised when a requested index reset could not be carried out.
   * Failing the startup is deliberate: the request (missing row, sentinel or in-process
   * registration) is only consumed by a successful wipe and recreate, so the next boot retries it.
   * Tolerating the failure would either leave a still-populated index behind a cursor that fetches
   * nothing (the sentinel: a silently frozen model) or leave the model without an index.
   *
   * @param modelName the engine model name
   * @param indexName the engine index name (or alias) that could not be wiped or recreated
   * @param failure what did not happen, e.g. {@code "its index could not be deleted"}
   * @return the failure message, naming the index and the manual recovery steps
   */
  public static String indexResetFailedMessage(String modelName, String indexName, String failure) {
    return "Index reset requested for model '"
        + modelName
        + "' but "
        + failure
        + " (index '"
        + indexName
        + "'). The request is kept and retried at the next startup. Check the engine errors"
        + " logged above; to recover by hand, delete the index and its template in the engine and"
        + " restart the platform; to abandon the reset instead, set the model's cursor to epoch:"
        + " UPDATE indexing_status SET indexing_status_indexing_date = to_timestamp(0)"
        + " WHERE indexing_status_type = '"
        + modelName
        + "' (INSERT that row when it is missing) - the index is then re-fed from epoch without"
        + " being wiped.";
  }

  /**
   * Computes the next indexing cursor for a processed batch.
   *
   * <p>All {@code findForIndexing} queries fetch rows with a strictly-greater-than comparison on
   * the cursor. When a FULL batch ends on a timestamp shared by several rows, the LIMIT may have
   * cut the group in the middle: advancing the cursor to that timestamp would permanently skip the
   * cut-off rows. In that case the cursor only advances past the last fully included timestamp
   * group; the boundary group is re-fetched (and harmlessly re-upserted) on the next round.
   *
   * @param results the batch rows, ordered by ascending update timestamp
   * @param batchSize the fetch limit used to obtain the batch
   * @param modelName the model name (logging only)
   * @param log the caller's logger
   * @return the new cursor, or null when it cannot be computed (null timestamps)
   */
  public static Instant computeNewCursor(
      List<? extends EsBase> results, int batchSize, String modelName, Logger log) {
    Instant boundary = results.getLast().getBase_updated_at();
    if (boundary == null) {
      return null;
    }
    if (results.size() < batchSize) {
      // Partial batch: everything with a timestamp <= boundary has been fetched, safe to advance.
      return boundary;
    }
    // Full batch: find the greatest timestamp strictly before the boundary group.
    Instant lastComplete = null;
    for (EsBase row : results) {
      Instant ts = row.getBase_updated_at();
      if (ts != null
          && ts.isBefore(boundary)
          && (lastComplete == null || ts.isAfter(lastComplete))) {
        lastComplete = ts;
      }
    }
    if (lastComplete != null) {
      return lastComplete;
    }
    // Degenerate case: every row of a full batch shares the exact same timestamp. The cursor must
    // advance (or the loop would never progress), but ties beyond the batch limit may be skipped.
    log.warn(
        "Full indexing batch for model {} shares a single timestamp {} ({} rows): advancing past it, "
            + "rows beyond the batch limit with this exact timestamp may be skipped until their next update.",
        modelName,
        boundary,
        results.size());
    return boundary;
  }

  /**
   * Caps a computed cursor so it never gets closer to wall-clock than the configured grace window.
   *
   * <p>{@code @UpdateTimestamp} values are assigned when Hibernate flushes, but the rows only
   * become visible to the (read-committed) indexing fetch at commit. Without a grace window, a
   * transaction that commits after a concurrent sync round advanced the cursor leaves rows with
   * {@code updated_at <= cursor} that a strictly-greater-than fetch will never see again - the
   * documents stay stale in the engine forever while PostgreSQL is correct. Keeping the persisted
   * cursor at least {@code graceWindowSeconds} behind {@code now} means rows younger than the
   * window are re-fetched (and idempotently re-upserted) on every round until they age past it, so
   * any writer committing within the window is guaranteed to be indexed.
   *
   * @param cursor the cursor computed from the processed batch (see {@link #computeNewCursor})
   * @param now the current wall-clock instant
   * @param graceWindowSeconds the grace window in seconds; negative values are treated as zero so a
   *     misconfiguration can never push the cap into the future and re-enable the race
   * @return the capped cursor: {@code min(cursor, now - max(0, graceWindowSeconds))}
   */
  public static Instant capCursorToGraceWindow(
      Instant cursor, Instant now, long graceWindowSeconds) {
    Instant maxSafeCursor = now.minusSeconds(Math.max(0, graceWindowSeconds));
    return cursor.isAfter(maxSafeCursor) ? maxSafeCursor : cursor;
  }
}
