package io.openaev.database.repository;

import io.openaev.database.model.Agent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AgentRepository
    extends CrudRepository<Agent, String>, JpaSpecificationExecutor<Agent> {

  @Query(
      value =
          """
          SELECT a FROM Agent a
            WHERE a.asset.id = :assetId
              AND a.executedByUser = :user
              AND a.deploymentMode = :deployment
              AND a.privilege = :privilege
              AND a.parent IS NULL
              AND a.inject IS NULL
              AND a.executor.id = :executorId
          """)
  Optional<Agent> findByAssetExecutorIdUserDeploymentAndPrivilege(
      @Param("assetId") String assetId,
      @Param("user") String user,
      @Param("deployment") Agent.DEPLOYMENT_MODE deployment,
      @Param("privilege") Agent.PRIVILEGE privilege,
      @Param("executorId") String executorId);

  List<Agent> findByExecutorId(String executorId);

  List<Agent> findByExecutorIdAndTenantId(String executorId, String tenantId);

  List<Agent> findByExternalReferenceAndTenantId(String externalReference, String tenantId);

  @Modifying
  @Query(value = "DELETE FROM agents agent where agent.agent_id = :agentId;", nativeQuery = true)
  @Transactional
  void deleteByAgentId(String agentId);

  // Native agent deletion bypasses the JPA lifecycle: bump the parent asset's updated_at BEFORE
  // deleting so the polling indexer re-feeds the endpoint (and derived vulnerable-endpoint)
  // documents that denormalize agent data (privileges, activity), instead of keeping them stale.
  @Modifying
  @Query(
      value =
          "UPDATE assets SET asset_updated_at = now() "
              + "WHERE asset_id = (SELECT agent_asset FROM agents WHERE agent_id = :agentId);",
      nativeQuery = true)
  @Transactional
  void touchAssetOfAgent(@Param("agentId") String agentId);
}
