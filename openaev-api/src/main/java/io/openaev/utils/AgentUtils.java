package io.openaev.utils;

import io.openaev.database.model.*;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;

public class AgentUtils {

  private AgentUtils() {}

  public static final List<Endpoint.PLATFORM_TYPE> AVAILABLE_PLATFORMS =
      List.of(
          Endpoint.PLATFORM_TYPE.Linux,
          Endpoint.PLATFORM_TYPE.Windows,
          Endpoint.PLATFORM_TYPE.MacOS);

  public static final List<Endpoint.PLATFORM_ARCH> AVAILABLE_ARCHITECTURES =
      List.of(Endpoint.PLATFORM_ARCH.x86_64, Endpoint.PLATFORM_ARCH.arm64);

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
