package io.openaev.database.repository.attackpath;

import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow;
import io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow;
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

  /**
   * An endpoint's relations: the executions targeting it. A single indexed read using {@code
   * idx_ap_exec_sim_targetkey}; {@code targetKey} is the asset id or the raw value.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathExecutionRow("
          + "e.id, e.sourceKind, e.sourceAssetId, e.agentId, e.agentName, e.agentPrivilege, "
          + "e.sourceInjector, e.targetKind, e.targetAssetId, e.targetRawValue, e.targetKey, "
          + "e.targetHostname, e.targetIp, e.targetPlatform, e.payloadName, e.executedAt, "
          + "e.preventionStatus, e.detectionStatus, e.stepTemplateId) "
          + "FROM AttackPathExecution e "
          + "WHERE e.simulationId = :simulationId AND e.targetKey = :targetKey")
  List<AttackPathExecutionRow> findByTarget(
      @Param("simulationId") String simulationId, @Param("targetKey") String targetKey);

  /**
   * Number of executions of a simulation, used to pick full vs collapsed mode against a threshold.
   */
  @Query("SELECT count(e) FROM AttackPathExecution e WHERE e.simulationId = :simulationId")
  long countExecutions(@Param("simulationId") String simulationId);

  /**
   * Collapsed mode: one endpoint per {@code target_key}, with a representative of its frozen
   * display attributes ({@code max} per column, which is the constant value within one simulation)
   * and its prevented / total execution counts. A {@code GROUP BY}, so the per-execution rows are
   * never materialized; the tenant filter is added by the inspector.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEndpointGroupRow("
          + "e.targetKey, max(e.targetAssetId), max(e.targetHostname), max(e.targetIp), "
          + "max(e.targetPlatform), max(e.executedAt), "
          + "sum(case when e.preventionStatus = 'Prevented' then 1 else 0 end), count(e)) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId GROUP BY e.targetKey")
  List<AttackPathEndpointGroupRow> findEndpointGroups(@Param("simulationId") String simulationId);

  /**
   * Collapsed mode: one grouped edge per (source, target), with how many executions it groups. A
   * {@code GROUP BY}, so the per-execution rows are never materialized.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathEdgeGroupRow("
          + "e.sourceKind, e.sourceInjector, e.sourceAssetId, e.targetKey, count(e)) "
          + "FROM AttackPathExecution e WHERE e.simulationId = :simulationId "
          + "GROUP BY e.sourceKind, e.sourceInjector, e.sourceAssetId, e.targetKey")
  List<AttackPathEdgeGroupRow> findEdgeGroups(@Param("simulationId") String simulationId);
}
