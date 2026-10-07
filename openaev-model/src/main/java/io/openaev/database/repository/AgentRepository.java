package io.openaev.database.repository;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Asset;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface AgentRepository
    extends JpaRepository<Agent, String>, JpaSpecificationExecutor<Agent> {

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

  /**
   * True when the agent is a primary agent (no parent, not created for a specific inject) and its
   * asset is targeted by the inject, either directly or as a static member of one of the inject's
   * asset groups. Dynamic group membership is not covered here. Only ids are read: neither the
   * agent nor the inject is loaded.
   */
  @Query(
      """
      SELECT COUNT(a) > 0 FROM Agent a
        WHERE a.id = :agentId
          AND a.parent IS NULL
          AND a.inject IS NULL
          AND (EXISTS (SELECT 1 FROM Inject i JOIN i.assets ia
                        WHERE i.id = :injectId AND ia.id = a.asset.id)
            OR EXISTS (SELECT 1 FROM Inject i JOIN i.assetGroups g JOIN g.assets ga
                        WHERE i.id = :injectId AND ga.id = a.asset.id))
      """)
  boolean isPrimaryAgentAssetStaticallyTargetedByInject(
      @Param("agentId") String agentId, @Param("injectId") String injectId);

  /** The asset of the agent, only when it is a primary agent, without loading the agent. */
  @Query(
      "SELECT a.asset FROM Agent a WHERE a.id = :agentId AND a.parent IS NULL AND a.inject IS NULL")
  Optional<Asset> findPrimaryAgentAsset(@Param("agentId") String agentId);

  List<Agent> findByExecutorId(String executorId);

  @Query("SELECT a FROM Agent a WHERE a.executor.id = :executorId AND a.tenant.id = :tenantId")
  List<Agent> findByExecutorIdAndTenantId(
      @Param("executorId") String executorId, @Param("tenantId") String tenantId);

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query("DELETE FROM Agent a WHERE a.executor.id = :executorId AND a.tenant.id = :tenantId")
  void deleteAllByExecutorIdAndTenantId(
      @Param("executorId") String executorId, @Param("tenantId") String tenantId);

  List<Agent> findByExternalReferenceAndTenantId(String externalReference, String tenantId);

  long countByStatus(AgentStatus status);

  @Query(
      """
      SELECT a FROM Agent a
        WHERE a.tenant.id = :tenantId
          AND a.status = :status
          AND (a.lastSeen IS NULL OR a.lastSeen < :threshold)
      """)
  List<Agent> findStaleAgentsByTenantIdAndStatus(
      @Param("tenantId") String tenantId,
      @Param("threshold") Instant threshold,
      @Param("status") AgentStatus status);

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
