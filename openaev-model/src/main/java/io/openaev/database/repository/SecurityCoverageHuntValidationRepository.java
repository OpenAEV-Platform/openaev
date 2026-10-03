package io.openaev.database.repository;

import io.openaev.database.model.SecurityCoverageHuntValidation;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityCoverageHuntValidationRepository
    extends JpaRepository<SecurityCoverageHuntValidation, String> {

  /** Lock timeout hint value Hibernate renders as {@code SKIP LOCKED} ({@code LockOptions}). */
  String SKIP_LOCKED = "-2";

  List<SecurityCoverageHuntValidation> findAllByInjectIdIn(@NotNull Collection<String> injectIds);

  /**
   * The due validations of a status planned after {@code createdAfter}, oldest due first, each
   * locked until the transaction ends. Rows locked by another transaction are skipped ({@code FOR
   * UPDATE SKIP LOCKED}), so concurrent deliveries, from this instance or another one, never read
   * the same row. Must run inside a transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
  @Query(
      "select v from SecurityCoverageHuntValidation v"
          + " where v.status = :status and v.nextAttemptAt <= :now"
          + " and v.createdAt > :createdAfter"
          + " order by v.nextAttemptAt asc")
  List<SecurityCoverageHuntValidation> findDueForUpdateSkipLocked(
      @NotNull @Param("status") SecurityCoverageHuntValidation.Status status,
      @NotNull @Param("now") Instant now,
      @NotNull @Param("createdAfter") Instant createdAfter,
      @NotNull Pageable pageable);

  /**
   * The due validations of a status planned at or before {@code createdBefore}, locked like {@link
   * #findDueForUpdateSkipLocked}: the stale ones a delivery gives up, read apart from the fresh
   * ones so that a backlog of stale rows never takes their delivery slots. Must run inside a
   * transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
  @Query(
      "select v from SecurityCoverageHuntValidation v"
          + " where v.status = :status and v.nextAttemptAt <= :now"
          + " and v.createdAt <= :createdBefore"
          + " order by v.nextAttemptAt asc")
  List<SecurityCoverageHuntValidation> findStaleForUpdateSkipLocked(
      @NotNull @Param("status") SecurityCoverageHuntValidation.Status status,
      @NotNull @Param("now") Instant now,
      @NotNull @Param("createdBefore") Instant createdBefore,
      @NotNull Pageable pageable);

  /**
   * Loads validations with a row lock held until the transaction ends: concurrent outcome records
   * are serialized, and the second one sees the status the first one set. Must run inside a
   * transaction.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from SecurityCoverageHuntValidation v where v.id in :ids")
  List<SecurityCoverageHuntValidation> findAllByIdForUpdate(
      @NotNull @Param("ids") Collection<String> ids);
}
