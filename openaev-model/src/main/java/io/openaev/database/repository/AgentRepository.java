package io.openaev.database.repository;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
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
   *
   * <p>Native on purpose: a JPQL root on {@code Agent} or {@code Inject} would carry the v1 {@code
   * tenantFilter}, which reads the {@code TenantContext} thread-local. That thread-local is only
   * set on the tenant-prefixed routes, so on the non-prefixed implant routes it falls back to the
   * default tenant and rejects a valid agent of any other tenant. The tenant scope here is the
   * transaction's own ({@code TxCtx}): the v2-active {@code assets} and {@code asset_groups} are
   * rewritten by the statement inspector, and the agent is bound to its asset's tenant explicitly
   * since {@code agents} is still v1. {@code injects_assets} and {@code injects_asset_groups} are
   * reached through the scoped assets and groups only.
   */
  @Query(
      value =
          """
          SELECT COUNT(*) > 0 FROM agents ag
            JOIN assets a ON a.asset_id = ag.agent_asset AND a.tenant_id = ag.tenant_id
            WHERE ag.agent_id = :agentId
              AND ag.agent_parent IS NULL
              AND ag.agent_inject IS NULL
              AND (EXISTS (SELECT 1 FROM injects_assets ia
                            WHERE ia.inject_id = :injectId AND ia.asset_id = a.asset_id)
                OR EXISTS (SELECT 1 FROM injects_asset_groups iag
                            JOIN asset_groups g ON g.asset_group_id = iag.asset_group_id
                            JOIN asset_groups_assets aga ON aga.asset_group_id = g.asset_group_id
                            WHERE iag.inject_id = :injectId AND aga.asset_id = a.asset_id))
          """,
      nativeQuery = true)
  boolean isPrimaryAgentAssetStaticallyTargetedByInject(
      @Param("agentId") String agentId, @Param("injectId") String injectId);

  /**
   * True when the agent is a primary agent that was granted the inject at launch: it has an
   * expectation or an execution trace on it. Both only exist once the platform targeted the agent
   * (expectations at launch) or the agent was served the inject (start trace on payload retrieval),
   * so they outlive any later edit of the inject's targets. Only ids are read.
   *
   * <p>Native for the same reason as {@link #isPrimaryAgentAssetStaticallyTargetedByInject}: scoped
   * by the transaction's {@code TxCtx} through {@code assets}, not by the v1 filter on {@code
   * Agent}.
   */
  @Query(
      value =
          """
          SELECT COUNT(*) > 0 FROM agents ag
            JOIN assets a ON a.asset_id = ag.agent_asset AND a.tenant_id = ag.tenant_id
            WHERE ag.agent_id = :agentId
              AND ag.agent_parent IS NULL
              AND ag.agent_inject IS NULL
              AND (EXISTS (SELECT 1 FROM injects_expectations ie
                            WHERE ie.inject_id = :injectId AND ie.agent_id = ag.agent_id)
                OR EXISTS (SELECT 1 FROM execution_traces et
                            JOIN injects_statuses s ON s.status_id = et.execution_inject_status_id
                            WHERE s.status_inject = :injectId AND et.execution_agent_id = ag.agent_id))
          """,
      nativeQuery = true)
  boolean isPrimaryAgentGrantedInject(
      @Param("agentId") String agentId, @Param("injectId") String injectId);

  /**
   * The id of the asset of the agent, only when it is a primary agent, without loading the agent.
   * Native for the same reason as {@link #isPrimaryAgentAssetStaticallyTargetedByInject}: scoped by
   * the transaction's {@code TxCtx} through {@code assets}, not by the v1 filter on {@code Agent}.
   * Returns the id rather than the entity: a native query cannot hydrate the computed columns of
   * {@code Asset}, so the caller loads it through the asset repository.
   */
  @Query(
      value =
          """
          SELECT a.asset_id FROM assets a
            JOIN agents ag ON ag.agent_asset = a.asset_id AND ag.tenant_id = a.tenant_id
            WHERE ag.agent_id = :agentId
              AND ag.agent_parent IS NULL
              AND ag.agent_inject IS NULL
          """,
      nativeQuery = true)
  Optional<String> findPrimaryAgentAssetId(@Param("agentId") String agentId);

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
