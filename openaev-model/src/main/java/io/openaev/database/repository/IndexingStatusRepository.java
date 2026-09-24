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
   * Persists the cursor of a sync round unless the row requests an index reset (cursor at or beyond
   * {@link EsIndexingUtils#REINDEX_REQUESTED_THRESHOLD}): a round reads its cursor at the start and
   * persists the advanced one at the end, so a plain save would overwrite a reset request that
   * landed in between and lose it silently. Only the boot-time reset, once the index has actually
   * been wiped and recreated, may replace a reset request (through {@link #save}).
   *
   * <p>Native: the atomic {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE} compare-and-set cannot
   * be expressed through the session (a merge is unconditional). No session side effect is lost:
   * {@code indexing_status} is engine bookkeeping, neither indexed, audited nor streamed.
   *
   * @param type the engine model name
   * @param cursor the cursor to persist
   * @return true when the cursor was persisted, false when the row requests a reset
   */
  default boolean advanceCursorUnlessResetRequested(String type, Instant cursor) {
    return upsertCursorBelowThreshold(type, cursor, EsIndexingUtils.REINDEX_REQUESTED_THRESHOLD)
        > 0;
  }

  @Modifying
  @Transactional
  @Query(
      value =
          "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date)"
              + " VALUES (:type, :cursor)"
              + " ON CONFLICT (indexing_status_type)"
              + " DO UPDATE SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date"
              + " WHERE indexing_status.indexing_status_indexing_date < :resetThreshold",
      nativeQuery = true)
  int upsertCursorBelowThreshold(
      @Param("type") String type,
      @Param("cursor") Instant cursor,
      @Param("resetThreshold") Instant resetThreshold);
}
