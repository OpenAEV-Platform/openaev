package io.openaev.service;

import io.openaev.engine.model.EsBase;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Shared helpers for the incremental indexing loop of both engine implementations (Elasticsearch
 * and OpenSearch): cursor advancement that is safe at LIMIT boundaries, and classification of
 * deterministic (poison) bulk item errors.
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

  private EsIndexingUtils() {}

  /**
   * Returns true when the bulk item error type identifies a deterministic document-level failure
   * (mapping/parsing) that would fail identically on every retry.
   */
  public static boolean isPoisonError(String errorType) {
    return errorType != null && POISON_ERROR_TYPES.contains(errorType);
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

  /**
   * Computes the next cursor for a keyset-paged handler.
   *
   * <p>Such a handler pages on the total order {@code (base_updated_at, base_id)}, so the last row
   * of the batch is always a safe resume point: unlike {@link #computeNewCursor}, there is no
   * boundary group to step back from, and the cursor advances by the full batch every round even
   * when every row shares one timestamp.
   *
   * @param results the batch rows, ordered by ascending {@code (base_updated_at, base_id)}
   * @return the last row's {@code (base_updated_at, base_id)}, or null when its timestamp is null
   */
  public static IndexingCursor computeKeysetCursor(List<? extends EsBase> results) {
    EsBase last = results.getLast();
    Instant boundary = last.getBase_updated_at();
    if (boundary == null) {
      return null;
    }
    return new IndexingCursor(boundary, last.getBase_id());
  }

  /**
   * Applies {@link #capCursorToGraceWindow} to a keyset cursor.
   *
   * <p>When the cap moves the timestamp, the last id belongs to a later row and must be dropped:
   * with a null id the fetch degrades to {@code updated_at > cappedTs} and idempotently re-upserts
   * the whole boundary group, which is the safe behaviour. Keeping the id would resume after a row
   * that sits beyond the capped instant and skip everything in between.
   *
   * <p>Dropping the id also suspends keyset progress: a group of rows sharing one timestamp still
   * inside the window re-serves its first batch every round, and only advances once that timestamp
   * ages past the window. That is a stall, not the permanent skip this cursor exists to fix.
   */
  public static IndexingCursor capToGraceWindow(
      IndexingCursor cursor, Instant now, long graceWindowSeconds) {
    Instant capped = capCursorToGraceWindow(cursor.timestamp(), now, graceWindowSeconds);
    return capped.equals(cursor.timestamp()) ? cursor : new IndexingCursor(capped, null);
  }
}
