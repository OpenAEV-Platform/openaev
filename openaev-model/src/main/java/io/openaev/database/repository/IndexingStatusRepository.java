package io.openaev.database.repository;

import io.openaev.database.model.IndexingStatus;
import io.openaev.service.EsIndexingUtils;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface IndexingStatusRepository
    extends CrudRepository<IndexingStatus, String>, JpaSpecificationExecutor<IndexingStatus> {

  @NotNull
  Optional<IndexingStatus> findByType(@NotNull String type);

  /**
   * Persists the cursor of a sync round as a compare-and-set on the cursor the round READ at its
   * start: the row is written only if it still carries {@code readCursor}. A round reads its cursor
   * first and persists the advanced one last, so an unconditional write would clobber whatever
   * landed in between - a reset request (the {@link EsIndexingUtils#REINDEX_REQUESTED_CURSOR}
   * sentinel written by a migration), the epoch written by a boot-time reset after the index was
   * wiped and recreated (the recreated index would then permanently skip every row before the stale
   * cursor), or a peer replica's advance. A lost compare-and-set is harmless: the round's documents
   * are already in the index and whoever owns the cursor fetches them again.
   *
   * <p>The threshold condition is a second guard for the same sentinel (belt and braces: the
   * equality already refuses it). Bulk JPQL update on a row that is neither indexed, audited nor
   * streamed: no listener side effect is lost.
   *
   * @param type the engine model name
   * @param readCursor the cursor the round read at its start (the row existed)
   * @param cursor the cursor to persist
   * @param resetThreshold {@link EsIndexingUtils#REINDEX_REQUESTED_THRESHOLD}
   * @return 1 when the cursor was persisted, 0 when the row changed since it was read
   */
  @Modifying
  @Transactional
  @Query(
      "UPDATE IndexingStatus s SET s.lastIndexing = :cursor"
          + " WHERE s.type = :type AND s.lastIndexing = :readCursor"
          + " AND s.lastIndexing < :resetThreshold")
  int advanceCursorFrom(
      @Param("type") String type,
      @Param("readCursor") Instant readCursor,
      @Param("cursor") Instant cursor,
      @Param("resetThreshold") Instant resetThreshold);

  /**
   * Persists the first cursor of a model whose row was MISSING when the round read it (never
   * initialized, or reset requested by deleting the row): the row is created, never overwritten. A
   * row that appeared meanwhile (a boot-time reset writing epoch, a peer's first batch) wins - see
   * {@link #advanceCursorFrom} for why a stale write must never land.
   *
   * <p>Native: the atomic {@code INSERT ... ON CONFLICT DO NOTHING} cannot be expressed through the
   * session (a save of a new entity fails on the duplicate key instead of yielding). No session
   * side effect is lost: {@code indexing_status} is engine bookkeeping, neither indexed, audited
   * nor streamed.
   *
   * @param type the engine model name
   * @param cursor the cursor to persist
   * @return 1 when the row was created, 0 when a row already exists
   */
  @Modifying
  @Transactional
  @Query(
      value =
          "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date)"
              + " VALUES (:type, :cursor)"
              + " ON CONFLICT (indexing_status_type) DO NOTHING",
      nativeQuery = true)
  int insertCursorIfAbsent(@Param("type") String type, @Param("cursor") Instant cursor);

  /**
   * Re-asserts the epoch cursor of a model after a boot-time reset, unless the row carries a new
   * reset request (cursor at or beyond {@code resetThreshold}) or is missing (a reset requested by
   * deleting the row must stay missing). Repairs the one write the compare-and-set cannot refuse: a
   * pod running a version older than this one persists its cursor unconditionally, and a round of
   * it that was in flight from before a reset migration committed until after the new pod wiped and
   * recreated the index moves the cursor from epoch to its stale value. Re-feeding from epoch a
   * second time is idempotent (upserts). Bulk JPQL update, same justification as {@link
   * #advanceCursorFrom}.
   *
   * @param type the engine model name
   * @param epoch the cursor to re-assert ({@link Instant#EPOCH})
   * @param resetThreshold {@link EsIndexingUtils#REINDEX_REQUESTED_THRESHOLD}
   * @return 1 when the cursor was re-asserted, 0 when the row is missing or requests a reset
   */
  @Modifying
  @Transactional
  @Query(
      "UPDATE IndexingStatus s SET s.lastIndexing = :epoch"
          + " WHERE s.type = :type AND s.lastIndexing < :resetThreshold")
  int reassertCursorUnlessResetRequested(
      @Param("type") String type,
      @Param("epoch") Instant epoch,
      @Param("resetThreshold") Instant resetThreshold);
}
