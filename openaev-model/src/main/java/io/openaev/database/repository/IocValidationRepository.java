package io.openaev.database.repository;

import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IocValidationRepository
    extends JpaRepository<IocValidation, String>, JpaSpecificationExecutor<IocValidation> {

  Optional<IocValidation> findByExternalIdAndTenantId(String externalId, String tenantId);

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
