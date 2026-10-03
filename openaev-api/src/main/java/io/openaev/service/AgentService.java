package io.openaev.service;

import io.openaev.database.model.Agent;
import io.openaev.database.repository.AgentRepository;
import io.openaev.utils.AgentUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AgentService {

  @PersistenceContext private EntityManager entityManager;

  private final AgentRepository agentRepository;

  public Optional<Agent> getAgentForAnAssetByExecutorId(
      String assetId,
      String user,
      Agent.DEPLOYMENT_MODE deploymentMode,
      Agent.PRIVILEGE privilege,
      String executorId) {
    return agentRepository.findByAssetExecutorIdUserDeploymentAndPrivilege(
        assetId, user, deploymentMode, privilege, executorId);
  }

  /**
   * Find an agent by id, only when it is a primary agent (no parent and not created for a specific
   * inject).
   *
   * @param agentId the agent id
   * @return the primary agent, or empty if it does not exist or is not a primary agent
   */
  public Optional<Agent> findPrimaryAgent(@NotBlank final String agentId) {
    return agentRepository.findById(agentId).filter(AgentUtils::isPrimaryAgent);
  }

  /**
   * True when the agent's asset is targeted by the inject, either directly or as a static member of
   * one of the inject's asset groups. Dynamic group membership is not covered here.
   *
   * @param agentId the agent id
   * @param injectId the inject id
   * @return whether the agent's asset is a static target of the inject
   */
  public boolean isAgentAssetStaticallyTargetedByInject(
      @NotBlank final String agentId, @NotBlank final String injectId) {
    return agentRepository.isAgentAssetStaticallyTargetedByInject(agentId, injectId);
  }

  public List<Agent> getAgentsByExecutorId(String executorId) {
    return agentRepository.findByExecutorId(executorId);
  }

  public List<Agent> getAgentsByExecutorIdAndTenantId(
      @NotBlank String executorId, @NotBlank String tenantId) {
    return agentRepository.findByExecutorIdAndTenantId(executorId, tenantId);
  }

  public Agent createOrUpdateAgent(@NotNull final Agent agent) {
    return this.agentRepository.save(agent);
  }

  @Transactional
  public List<Agent> saveAllAgents(List<Agent> agents) {
    List<Agent> agentsSaved = new ArrayList<>();
    // Improve perfs for save all
    for (int i = 0; i < agents.size(); i++) {
      agentsSaved.add(agentRepository.save(agents.get(i)));
      // Flush and clear the session every 50 (batch_size property) inserts
      if (i % 50 == 0) {
        entityManager.flush();
        entityManager.clear();
      }
    }
    return agentsSaved;
  }

  public void deleteAgent(@NotBlank final String agentId) {
    // Touch first (the subselect needs the agent row), so the search engine re-feeds the
    // endpoint documents that denormalize this agent's data once it is gone.
    this.agentRepository.touchAssetOfAgent(agentId);
    this.agentRepository.deleteByAgentId(agentId);
  }

  public List<Agent> findByExternalReference(String externalReference, String tenantId) {
    return agentRepository.findByExternalReferenceAndTenantId(externalReference, tenantId);
  }
}
