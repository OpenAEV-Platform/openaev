package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AttackPathExecutionRemediationRepository
    extends JpaRepository<AttackPathExecutionRemediation, String> {

  List<AttackPathExecutionRemediation> findByStepId(String stepId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "DELETE FROM AttackPathExecutionRemediation r "
          + "WHERE r.stepId IN ("
          + "SELECT DISTINCT e.stepId FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.stepId IS NOT NULL)")
  void deleteAllBySimulationId(@Param("simulationId") String simulationId);
}
