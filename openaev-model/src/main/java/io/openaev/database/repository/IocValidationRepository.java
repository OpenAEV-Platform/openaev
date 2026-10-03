package io.openaev.database.repository;

import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IocValidationRepository
    extends JpaRepository<IocValidation, String>, JpaSpecificationExecutor<IocValidation> {

  Optional<IocValidation> findByExternalIdAndTenantId(String externalId, String tenantId);

  /**
   * Takes a transaction-scoped Postgres advisory lock on {@code key}, released at commit/rollback
   * and held across API nodes. Serialises the intake of one OpenCTI request: a replay delivered to
   * another node waits for the first insert to commit and then finds it, instead of racing on the
   * {@code (external_id, tenant_id)} unique constraint. Native because JPQL cannot call {@code
   * pg_advisory_xact_lock}; wrapped as {@code SELECT 1 FROM (...)} so the {@code void} function
   * maps to a scalar. Must run inside a transaction.
   */
  @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked", nativeQuery = true)
  Integer lockRequestIntake(@Param("key") long key);

  /**
   * Loads a validation with a row lock held until the transaction ends: concurrent approve and
   * reject decisions are serialized, and the second one sees the status the first one set.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from IocValidation v where v.id = :id")
  Optional<IocValidation> findByIdForUpdate(@Param("id") String id);

  /** Id and tenant of every validation in one of the given statuses, for the background sweep. */
  @Query(
      "select v.id as id, v.tenant.id as tenantId from IocValidation v where v.status in :statuses")
  List<IocValidationRef> findRefsByStatusIn(
      @Param("statuses") Collection<IocValidationStatus> statuses);

  /** Id and tenant of every validation whose current status OpenCTI has not acknowledged yet. */
  @Query(
      "select v.id as id, v.tenant.id as tenantId from IocValidation v"
          + " where v.lifecycleSyncedStatus is null or v.lifecycleSyncedStatus <> v.status")
  List<IocValidationRef> findRefsWithPendingLifecycleSync();

  /** Id and tenant of every finished validation whose result bundle OpenCTI has not received. */
  @Query(
      "select v.id as id, v.tenant.id as tenantId from IocValidation v"
          + " where v.status in :statuses and v.resultsPushedAt is null")
  List<IocValidationRef> findRefsWithPendingResultsPush(
      @Param("statuses") Collection<IocValidationStatus> statuses);

  boolean existsBySimulationId(String simulationId);

  interface IocValidationRef {
    String getId();

    String getTenantId();
  }
}
