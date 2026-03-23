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
          "SELECT a.* FROM agents a "
              + "WHERE a.agent_asset = :assetId AND a.agent_executed_by_user = :user "
              + "AND a.agent_deployment_mode = :deployment AND a.agent_privilege = :privilege "
              + "AND a.agent_parent IS NULL AND a.agent_inject IS NULL "
              + "AND a.agent_executor = :executorId",
      nativeQuery = true)
  Optional<Agent> findByAssetExecutorIdUserDeploymentAndPrivilege(
      @Param("assetId") String assetId,
      @Param("user") String user,
      @Param("deployment") String deployment,
      @Param("privilege") String privilege,
      @Param("executorId") String executorId);

  List<Agent> findByExecutorId(String executorId);

  List<Agent> findByExternalReferenceAndTenantId(String externalReference, String tenantId);

  @Modifying
  @Query(value = "DELETE FROM agents agent where agent.agent_id = :agentId;", nativeQuery = true)
  @Transactional
  void deleteByAgentId(String agentId);
}
