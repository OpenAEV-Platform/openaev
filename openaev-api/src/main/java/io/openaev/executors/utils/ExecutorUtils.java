package io.openaev.executors.utils;

import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE;

import io.openaev.database.model.Agent;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ExecutorUtils {

  /**
   * Remove all Inactive agents from given agent list
   *
   * @param agents to filter
   * @return filtered list
   */
  public Set<Agent> removeInactiveAgentsFromAgents(Set<Agent> agents) {
    Set<Agent> agentsToFilter = new HashSet<>(agents);
    Set<Agent> inactiveAgents = foundInactiveAgents(agents);
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
    Set<Agent> inactiveAgents = foundAgentsWithoutExecutor(agents);
    agentsToFilter.removeAll(inactiveAgents);
    return agentsToFilter;
  }

  /**
   * Found all inactive agents from a list of agents
   *
   * @param agents to filter
   * @return inactives agents
   */
  public Set<Agent> foundInactiveAgents(Set<Agent> agents) {
    return agents.stream().filter(agent -> !agent.isActive()).collect(Collectors.toSet());
  }

  /**
   * Found all agents whitout executor from a list of agents
   *
   * @param agents to filter
   * @return agents without executor
   */
  public Set<Agent> foundAgentsWithoutExecutor(Set<Agent> agents) {
    return agents.stream().filter(agent -> agent.getExecutor() == null).collect(Collectors.toSet());
  }

  /**
   * Found all CrowdStrike agents from a list of agents
   *
   * @param agents to filter
   * @return founded crowdstrike agents
   */
  public Set<Agent> foundCrowdstrikeAgents(Set<Agent> agents) {
    return agents.stream()
        .filter(agent -> CROWDSTRIKE_EXECUTOR_TYPE.equals(agent.getExecutor().getType()))
        .collect(Collectors.toSet());
  }
}
