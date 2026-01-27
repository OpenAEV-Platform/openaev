package io.openaev.service;

import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
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

  public Optional<Agent> getAgentForAnAsset(
      String assetId,
      String user,
      Agent.DEPLOYMENT_MODE deploymentMode,
      Agent.PRIVILEGE privilege,
      String executor) {
    return agentRepository.findByAssetExecutorUserDeploymentAndPrivilege(
        assetId, user, deploymentMode.name(), privilege.name(), executor);
  }

  public List<Agent> getAgentsForExecution() {
    return agentRepository.findForExecution();
  }

  public List<Agent> getAgentsByExecutorType(String executor) {
    return agentRepository.findByExecutorType(executor);
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
    this.agentRepository.deleteByAgentId(agentId);
  }

  public List<Agent> findByExternalReference(String externalReference) {
    return agentRepository.findByExternalReference(externalReference);
  }

  public Tuple getAgentMetrics(Iterable<Executor> agentExecutors) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createQuery(Tuple.class);

    Root<Agent> root = cq.from(Agent.class);
    List<Selection<?>> selections = new ArrayList<>();

    selections.add(
        cb.count(cb.selectCase().when(cb.isNull(root.get("parent")), 1)).alias("total_agents"));
    selections.add(countAgentsByField(cb, root, "deploymentMode", "session", "session_agents"));
    selections.add(countAgentsByField(cb, root, "deploymentMode", "service", "service_agents"));
    selections.add(countAgentsByField(cb, root, "privilege", "standard", "user_agents"));
    selections.add(countAgentsByField(cb, root, "privilege", "admin", "admin_agents"));

    // Dynamically add COUNT for each Executor
    for (Executor executor : agentExecutors) {
      selections.add(
          countAgentsByField(cb, root, "executor", executor, "agent_" + executor.getType()));
    }

    cq.multiselect(selections);

    // Execute the query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    return query.getSingleResult();
  }

  private Selection<Long> countAgentsByField(
      CriteriaBuilder cb, Root<Agent> root, String field, Object value, String alias) {
    return cb.count(
            cb.selectCase()
                .when(cb.and(cb.isNull(root.get("parent")), cb.equal(root.get(field), value)), 1))
        .alias(alias);
  }
}
