package io.openaev.database.repository;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.raw.RawSecurityCoverageSendJobTenant;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityCoverageSendJobRepository
    extends CrudRepository<SecurityCoverageSendJob, String>,
        JpaSpecificationExecutor<SecurityCoverageSendJob> {

  @Query("select scsj from SecurityCoverageSendJob scsj where scsj.simulation = ?1")
  Optional<SecurityCoverageSendJob> findBySimulation(@NotBlank Exercise simulation);

  /**
   * The send jobs in a status since before the given instant, with their simulation and its tenant.
   * The join on the simulation keeps only the jobs the caller's scope can reach.
   */
  @Query(
      value =
          """
    SELECT scsj.security_coverage_send_job_id, e.exercise_id, e.tenant_id
    FROM security_coverage_send_job scsj
    JOIN exercises e ON e.exercise_id = scsj.security_coverage_send_job_simulation
    WHERE scsj.security_coverage_send_job_status = :status
      AND scsj.security_coverage_send_job_updated_at < :updatedAtBefore
    """,
      nativeQuery = true)
  List<RawSecurityCoverageSendJobTenant> findWithTenantByStatusAndUpdatedAtBefore(
      String status, Instant updatedAtBefore);

  @Query(
      value =
          """
    SELECT scsj.* FROM security_coverage_send_job scsj
    WHERE scsj.security_coverage_send_job_id IN :ids
    FOR UPDATE
    """,
      nativeQuery = true)
  List<SecurityCoverageSendJob> findAllByIdForUpdate(List<String> ids);
}
