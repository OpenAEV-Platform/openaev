package io.openaev.executors.utils;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.AssetAgentJobRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutorUtils {

  private final AssetAgentJobRepository assetAgentJobRepository;

  @Value("${openaev.agent.queue-threshold:0}")
  private int agentQueueThreshold;

  /**
   * Remove all Inactive agents from given agent list
   *
   * @param agents to filter
   * @return filtered list
   */
  public Set<Agent> removeInactiveAgentsFromAgents(Set<Agent> agents) {
    Set<Agent> agentsToFilter = new HashSet<>(agents);
    Set<Agent> inactiveAgents = findInactiveAgents(agents);
    agentsToFilter.removeAll(inactiveAgents);
    return agentsToFilter;
  }

  /**
   * Remove all agents without executor from given agent list
   *
   * @param agents to filter
   * @return filtered list
   */
  public Set<Agent> removeAgentsWithoutExecutorFromAgents(Set<Agent> agents) {
    Set<Agent> agentsToFilter = new HashSet<>(agents);
    Set<Agent> inactiveAgents = findAgentsWithoutExecutor(agents);
    agentsToFilter.removeAll(inactiveAgents);
    return agentsToFilter;
  }

  /**
   * Find all inactive agents from a list of agents
   *
   * @param agents to filter
   * @return inactive agents
   */
  public Set<Agent> findInactiveAgents(Set<Agent> agents) {
    return agents.stream().filter(agent -> !agent.isActive()).collect(Collectors.toSet());
  }

  /**
   * Find all agents without executor from a list of agents
   *
   * @param agents to filter
   * @return agents without executor
   */
  public Set<Agent> findAgentsWithoutExecutor(Set<Agent> agents) {
    return agents.stream().filter(agent -> agent.getExecutor() == null).collect(Collectors.toSet());
  }

  /**
   * Find all agents from a list of agents and an executor type
   *
   * @param agents to filter
   * @param executorType to filter
   * @return agents matching the given executor type
   */
  public Set<Agent> findAgentsByExecutorType(Set<Agent> agents, String executorType) {
    return agents.stream()
        .filter(agent -> executorType.equals(agent.getExecutor().getType()))
        .collect(Collectors.toSet());
  }

  /**
   * Find all agents from the given OS
   *
   * @param agents to filter
   * @param platform to filter
   * @return agents matching the given OS
   */
  public static List<Agent> getAgentsFromOS(List<Agent> agents, Endpoint.PLATFORM_TYPE platform) {
    return agents.stream()
        .filter(agent -> ((Endpoint) agent.getAsset()).getPlatform().equals(platform))
        .toList();
  }

  /**
   * Find all agents from the given OS and arch
   *
   * @param agents to filter
   * @param platform to filter
   * @param arch to filter
   * @return agents matching the given OS and arch
   */
  public static List<Agent> getAgentsFromOSAndArch(
      List<Agent> agents, Endpoint.PLATFORM_TYPE platform, Endpoint.PLATFORM_ARCH arch) {
    return agents.stream()
        .filter(
            agent -> {
              Endpoint endpoint = (Endpoint) agent.getAsset();
              return endpoint.getPlatform().equals(platform) && endpoint.getArch().equals(arch);
            })
        .toList();
  }

  /**
   * Find all overloaded agents from a list of agents. An agent is overloaded when its pending job
   * count exceeds the configured queue threshold. If the threshold is 0 (disabled), returns an
   * empty set.
   *
   * @param agents to check
   * @return overloaded agents
   */
  public Set<Agent> findOverloadedAgents(Set<Agent> agents) {
    if (agentQueueThreshold <= 0 || agents.isEmpty()) {
      return Set.of();
    }
    Set<String> agentIds = agents.stream().map(Agent::getId).collect(Collectors.toSet());
    Set<Object[]> counts = assetAgentJobRepository.countPendingJobsByAgentIds(agentIds);
    Map<String, Long> countByAgentId =
        counts.stream().collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    return agents.stream()
        .filter(
            agent -> {
              Long count = countByAgentId.get(agent.getId());
              return count != null && count >= agentQueueThreshold;
            })
        .collect(Collectors.toSet());
  }
}
