package io.openbas.utils;

import io.openbas.database.model.*;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;

public class AgentUtils {

  private AgentUtils() {}

  public static final List<String> AVAILABLE_PLATFORMS =
      List.of(
          Endpoint.PLATFORM_TYPE.Linux.name().toLowerCase(),
          Endpoint.PLATFORM_TYPE.Windows.name().toLowerCase(),
          Endpoint.PLATFORM_TYPE.MacOS.name().toLowerCase());

  public static final List<String> AVAILABLE_ARCHITECTURES =
      List.of(
          Endpoint.PLATFORM_ARCH.x86_64.name().toLowerCase(),
          Endpoint.PLATFORM_ARCH.arm64.name().toLowerCase());

  public static List<Agent> getActiveAgents(Asset asset, Inject inject) {
    return ((Endpoint) Hibernate.unproxy(asset))
        .getAgents().stream().filter(agent -> isValidAgent(inject, agent)).toList();
  }

  public static boolean isValidAgent(Inject inject, Agent agent) {
    return isPrimaryAgent(agent) && hasOnlyValidTraces(inject, agent) && agent.isActive();
  }

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

  public static boolean isPrimaryAgent(Agent agent) {
    return agent.getParent() == null && agent.getInject() == null;
  }

  public static List<Agent> getPrimaryAgents(Endpoint endpoint) {
    return endpoint.getAgents().stream()
        .filter(AgentUtils::isPrimaryAgent)
        .collect(Collectors.toList());
  }
}
