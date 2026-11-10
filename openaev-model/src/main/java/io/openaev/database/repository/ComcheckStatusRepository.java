package io.openaev.database.repository;

import io.openaev.database.model.ComcheckStatus;
import io.openaev.database.raw.RawComcheckTenant;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComcheckStatusRepository
    extends CrudRepository<ComcheckStatus, String>, JpaSpecificationExecutor<ComcheckStatus> {

  @NotNull
  Optional<ComcheckStatus> findById(@NotNull String id);

  /**
   * The running comchecks that still have a status to send, with their simulation's tenant. The
   * join on the simulation keeps only the comchecks the caller's scope can reach.
   */
  @Query(
      value =
          """
    SELECT DISTINCT c.comcheck_id, e.tenant_id FROM comchecks_statuses s
    JOIN comchecks c ON c.comcheck_id = s.status_comcheck
    JOIN exercises e ON e.exercise_id = c.comcheck_exercise
    WHERE s.status_receive_date IS NULL
      AND s.status_sent_date IS NULL
      AND c.comcheck_state = 'RUNNING'
    """,
      nativeQuery = true)
  List<RawComcheckTenant> findComchecksToSendWithTenant();
}
