package io.openaev.api.capabilities;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityGroup;
import io.openaev.database.model.CapabilityScope;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CapabilityMapper {

  private CapabilityMapper() {}

  public static CapabilityOutput toOutput(Capability capability, List<CapabilityOutput> children) {
    return new CapabilityOutput(
        capability.name(), capability.isCheckable(), scopes(capability), children);
  }

  public static CapabilityOutput toOutput(
      CapabilityGroup capability, List<Capability> groupRoots, List<CapabilityOutput> children) {
    return new CapabilityOutput(capability.name(), false, scopes(groupRoots), children);
  }

  private static Set<CapabilityScope> scopes(Capability cap) {
    return Set.copyOf(cap.getScopes());
  }

  private static Set<CapabilityScope> scopes(List<Capability> capabilities) {
    return capabilities.stream().flatMap(c -> c.getScopes().stream()).collect(Collectors.toSet());
  }
}
