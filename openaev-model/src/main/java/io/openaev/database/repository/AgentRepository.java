package io.openaev.database.repository;

import io.openaev.database.model.Agent;
import io.openaev.database.raw.RawAgent;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
          "SELECT a.* FROM agents a left join executors ex on a.agent_executor = ex.executor_id "
              + "where a.agent_asset = :assetId and a.agent_executed_by_user = :user and a.agent_deployment_mode = :deployment "
              + "and a.agent_privilege = :privilege and a.agent_parent is null and a.agent_inject is null and ex.executor_type = :executor",
      nativeQuery = true)
  Optional<Agent> findByAssetExecutorUserDeploymentAndPrivilege(
      @Param("assetId") String assetId,
      @Param("user") String user,
      @Param("deployment") String deployment,
      @Param("privilege") String privilege,
      @Param("executor") String executor);

  @Query(
      value =
          "SELECT a.* FROM agents a left join executors ex on a.agent_executor = ex.executor_id "
              + "where ex.executor_type = :executor",
      nativeQuery = true)
  List<Agent> findByExecutorType(@Param("executor") String executor);

  List<Agent> findByExternalReference(String externalReference);

  /**
   * Returns the agents for Caldera execution
   *
   * @return the list of agents
   */
  @Query(
      value =
          "SELECT a.* FROM agents a WHERE a.agent_parent is not null and a.agent_inject is not null;",
      nativeQuery = true)
  List<Agent> findForExecution();

  // TODO : understand why the generic deleteById from Hibernate doesn't work
  @Modifying
  @Query(value = "DELETE FROM agents agent where agent.agent_id = :agentId;", nativeQuery = true)
  @Transactional
  void deleteByAgentId(String agentId);

  @Query(
      value =
          "SELECT ag.agent_id, "
              + "ag.agent_executed_by_user, "
              + "ex.executor_type "
              + "FROM agents ag "
              + "Left JOIN executors ex ON ag.agent_executor = ex.executor_id "
              + "WHERE ag.agent_id IN :agentIds ;",
      nativeQuery = true)
  Set<RawAgent> rawAgentByIds(Set<String> agentIds);
}
