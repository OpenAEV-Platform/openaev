package io.openaev.utils.mapper;

import static java.util.Collections.emptyList;

import io.openaev.database.model.Agent;
import io.openaev.rest.asset.endpoint.form.AgentOutput;
import io.openaev.rest.asset.endpoint.form.ExecutorOutput;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentMapper {

  public Set<AgentOutput> toAgentOutputs(List<Agent> agents) {
    return Optional.ofNullable(agents).orElse(emptyList()).stream()
        .map(this::toAgentOutput)
        .collect(Collectors.toSet());
  }

  public AgentOutput toAgentOutput(Agent agent) {
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
          ExecutorOutput.builder()
              .id(agent.getExecutor().getId())
              .name(agent.getExecutor().getName())
              .type(agent.getExecutor().getType())
              .build());
    }
    return builder.build();
  }
}
