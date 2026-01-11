package io.openaev.utils;

import io.openaev.database.model.*;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;

/**
 * Utility class for agent-related operations and validations.
 *
 * <p>Provides helper methods for filtering, validating, and retrieving agents associated with
 * assets and injects. Agents are software components deployed on endpoints that execute inject
 * commands during simulations.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Agent
 * @see io.openaev.database.model.Endpoint
 */
public class AgentUtils {

  private AgentUtils() {}

  /**
   * List of supported platform types in lowercase format.
   *
   * <p>Contains: linux, windows, macos
   */
  public static final List<String> AVAILABLE_PLATFORMS =
      List.of(
          Endpoint.PLATFORM_TYPE.Linux.name().toLowerCase(),
          Endpoint.PLATFORM_TYPE.Windows.name().toLowerCase(),
          Endpoint.PLATFORM_TYPE.MacOS.name().toLowerCase());

  /**
   * List of supported CPU architectures in lowercase format.
   *
   * <p>Contains: x86_64, arm64
   */
  public static final List<String> AVAILABLE_ARCHITECTURES =
      List.of(
          Endpoint.PLATFORM_ARCH.x86_64.name().toLowerCase(),
          Endpoint.PLATFORM_ARCH.arm64.name().toLowerCase());

  /**
   * Retrieves all active and valid agents from an asset for a specific inject.
   *
   * <p>This method unproxies the asset to access the underlying endpoint and filters its agents
   * based on validity criteria for the given inject.
   *
   * @param asset the asset (endpoint) containing the agents
   * @param inject the inject context used for validation
   * @return a list of active agents that pass all validation checks
   * @see #isValidAgent(Inject, Agent)
   */
  public static List<Agent> getActiveAgents(Asset asset, Inject inject) {
    return ((Endpoint) Hibernate.unproxy(asset))
        .getAgents().stream().filter(agent -> isValidAgent(inject, agent)).toList();
  }

  /**
   * Validates whether an agent is suitable for executing an inject.
   *
   * <p>An agent is considered valid if it meets all of the following criteria:
   *
   * <ul>
   *   <li>It is a primary agent (not a child or inject-specific agent)
   *   <li>It has no error or inactive traces for the given inject
   *   <li>It is currently active
   * </ul>
   *
   * @param inject the inject to validate against
   * @param agent the agent to validate
   * @return {@code true} if the agent is valid for the inject, {@code false} otherwise
   */
  public static boolean isValidAgent(Inject inject, Agent agent) {
    return isPrimaryAgent(agent) && hasOnlyValidTraces(inject, agent) && agent.isActive();
  }

  /**
   * Checks if an agent has only valid execution traces for a specific inject.
   *
   * <p>An agent has valid traces if none of its traces for the inject have an ERROR or
   * AGENT_INACTIVE status. If no traces exist, the agent is considered valid by default.
   *
   * @param inject the inject to check traces for
   * @param agent the agent whose traces are being validated
   * @return {@code true} if no invalid traces exist, {@code false} if error or inactive traces are
   *     found
   */
  public static boolean hasOnlyValidTraces(Inject inject, Agent agent) {
    return inject
        .getStatus()
        .map(InjectStatus::getTraces)
        .map(
            traces ->
                Boolean.valueOf(
                    traces.stream()
                        .noneMatch(
                            trace ->
                                trace.getAgent() != null
                                    && trace.getAgent().getId().equals(agent.getId())
                                    && (ExecutionTraceStatus.ERROR.equals(trace.getStatus())
                                        || ExecutionTraceStatus.AGENT_INACTIVE.equals(
                                            trace.getStatus())))))
        .orElse(Boolean.TRUE)
        .booleanValue(); // If there are no traces, return true by default
  }

  /**
   * Determines if an agent is a primary agent.
   *
   * <p>A primary agent is a standalone agent that is not a child of another agent and is not
   * created specifically for an inject execution.
   *
   * @param agent the agent to check
   * @return {@code true} if the agent has no parent and no associated inject
   */
  public static boolean isPrimaryAgent(Agent agent) {
    return agent.getParent() == null && agent.getInject() == null;
  }

  /**
   * Retrieves all primary agents from an endpoint.
   *
   * @param endpoint the endpoint to get primary agents from
   * @return a list of primary agents associated with the endpoint
   * @see #isPrimaryAgent(Agent)
   */
  public static List<Agent> getPrimaryAgents(Endpoint endpoint) {
    return endpoint.getAgents().stream()
        .filter(AgentUtils::isPrimaryAgent)
        .collect(Collectors.toList());
  }
}
