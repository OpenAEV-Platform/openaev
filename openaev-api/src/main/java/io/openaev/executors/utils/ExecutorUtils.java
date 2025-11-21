package io.openaev.executors.utils;

import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE;
import static io.openaev.executors.sentinelone.service.SentinelOneExecutorService.SENTINELONE_EXECUTOR_TYPE;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import java.util.HashSet;
import java.util.List;
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

  /**
   * Found all SentinelOne agents from a list of agents
   *
   * @param agents to filter
   * @return founded sentinelone agents
   */
  public Set<Agent> foundSentineloneAgents(Set<Agent> agents) {
    return agents.stream()
        .filter(agent -> SENTINELONE_EXECUTOR_TYPE.equals(agent.getExecutor().getType()))
        .collect(Collectors.toSet());
  }

  /**
   * Found all agents from the given OS
   *
   * @param agents to filter
   * @param platform to filter
   * @return founded agents from the given OS
   */
  public static List<Agent> getAgentsFromOS(List<Agent> agents, Endpoint.PLATFORM_TYPE platform) {
    return agents.stream()
        .filter(agent -> ((Endpoint) agent.getAsset()).getPlatform().equals(platform))
        .toList();
  }

  /**
   * Found all agents from the given OS and arch
   *
   * @param agents to filter
   * @param platform to filter
   * @param arch to filter
   * @return founded agents from the given OS and arch
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
}
