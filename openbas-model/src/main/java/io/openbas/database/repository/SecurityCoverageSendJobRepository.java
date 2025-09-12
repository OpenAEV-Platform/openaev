package io.openbas.database.repository;

import io.openbas.database.model.Exercise;
import io.openbas.database.model.SecurityCoverageSendJob;
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

  @Query(
      value =
          """
    SELECT scsj.* FROM security_coverage_send_job scsj
    WHERE scsj.security_coverage_send_job_status = :status
      AND scsj.security_coverage_send_job_updated_at < :updatedAtBefore
    """,
      nativeQuery = true)
  List<SecurityCoverageSendJob> findByStatusAndUpdatedAtBeforeNoLock(
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
