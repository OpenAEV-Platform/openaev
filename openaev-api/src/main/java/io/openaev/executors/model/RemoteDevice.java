package io.openaev.executors.model;

import java.util.List;
import java.util.Set;

/** A device as the EDR reports it, with the groups it belongs to. */
public record RemoteDevice(AgentRegisterInput agent, Set<DeviceGroup> groups) {

  public static List<RemoteDevice> inGroup(List<AgentRegisterInput> agents, DeviceGroup group) {
    return agents.stream().map(agent -> new RemoteDevice(agent, Set.of(group))).toList();
  }

  public static List<RemoteDevice> withoutGroup(List<AgentRegisterInput> agents) {
    return agents.stream().map(agent -> new RemoteDevice(agent, Set.of())).toList();
  }
}
