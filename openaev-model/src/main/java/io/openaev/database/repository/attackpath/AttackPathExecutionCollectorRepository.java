package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecutionCollector;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AttackPathExecutionCollectorRepository
    extends JpaRepository<AttackPathExecutionCollector, String> {

  @Query(
      "SELECT c FROM AttackPathExecutionCollector c "
          + "WHERE c.executionId = :executionId AND c.tenant.id = :tenantId")
  List<AttackPathExecutionCollector> findByExecutionIdAndTenantId(
      @Param("executionId") String executionId, @Param("tenantId") String tenantId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "DELETE FROM AttackPathExecutionCollector c "
          + "WHERE c.executionId IN :executionIds AND c.tenant.id = :tenantId")
  void deleteAllByExecutionIdInAndTenantId(
      @Param("executionIds") List<String> executionIds, @Param("tenantId") String tenantId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "DELETE FROM AttackPathExecutionCollector c "
          + "WHERE c.simulationId = :simulationId AND c.tenant.id = :tenantId")
  void deleteAllBySimulationId(
      @Param("simulationId") String simulationId, @Param("tenantId") String tenantId);
}
