package io.openaev.utils.mapper;

import static java.util.Collections.emptyList;

import io.openaev.database.model.Agent;
import io.openaev.rest.asset.endpoint.form.AgentExecutorOutput;
import io.openaev.rest.asset.endpoint.form.AgentOutput;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mapper component for converting Agent entities to output DTOs.
 *
 * <p>Provides methods for transforming agent domain objects into API response objects, including
 * executor information when available.
 *
 * @see io.openaev.database.model.Agent
 * @see io.openaev.rest.asset.endpoint.form.AgentOutput
 */
@Component
@RequiredArgsConstructor
public class AgentMapper {

  /**
   * Converts a list of agents to a set of agent output DTOs.
   *
   * <p>Handles null input gracefully by returning an empty set.
   *
   * @param agents the list of agents to convert (may be null)
   * @return a set of agent output DTOs
   */
  public Set<AgentOutput> toAgentOutputs(List<Agent> agents) {
    return Optional.ofNullable(agents).orElse(emptyList()).stream()
        .map(AgentMapper::toAgentOutput)
        .collect(Collectors.toSet());
  }

  /**
   * Converts a single agent entity to an output DTO.
   *
   * <p>Includes agent properties such as privilege, deployment mode, activity status, version, and
   * executor information if available.
   *
   * @param agent the agent to convert
   * @return the agent output DTO
   */
  public static AgentOutput toAgentOutput(Agent agent) {
    AgentOutput.AgentOutputBuilder builder =
        AgentOutput.builder()
            .id(agent.getId())
            .privilege(agent.getPrivilege())
            .deploymentMode(agent.getDeploymentMode())
            .executedByUser(agent.getExecutedByUser())
            .isActive(agent.isActive())
            .agentVersion(agent.getVersion())
            .lastSeen(agent.getLastSeen());

    if (agent.getExecutor() != null) {
      builder.executor(
          AgentExecutorOutput.builder()
              .id(agent.getExecutor().getId())
              .name(agent.getExecutor().getName())
              .type(agent.getExecutor().getType())
              .build());
    }
    return builder.build();
  }
}
