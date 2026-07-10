package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.projection.AttackPathExecutionRow;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface AttackPathExecutionRepository extends CrudRepository<AttackPathExecution, String> {

  /**
   * Read A of the rebuild: the edges plus injector/endpoint/execution nodes of a simulation, as a
   * flat projection of the short display columns (never {@code command}/{@code terminal_output}).
   * JPQL, not native; the tenant filter is added by the statement inspector, so the query carries
   * only {@code simulationId}.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathExecutionRow("
          + "e.id, e.sourceKind, e.sourceAssetId, e.agentId, e.agentName, e.agentPrivilege, "
          + "e.sourceInjector, e.targetKind, e.targetAssetId, e.targetRawValue, e.targetKey, "
          + "e.targetHostname, e.targetIp, e.targetPlatform, e.payloadName, e.executedAt, "
          + "e.preventionStatus, e.detectionStatus, e.stepTemplateId) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId")
  List<AttackPathExecutionRow> findGraphRows(@Param("simulationId") String simulationId);
}
