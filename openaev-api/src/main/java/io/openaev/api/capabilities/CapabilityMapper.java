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
        capability.name(), capability.isCheckable(), scopeNames(capability), children);
  }

  public static CapabilityOutput toOutput(
      CapabilityGroup capability, List<Capability> groupRoots, List<CapabilityOutput> children) {
    return new CapabilityOutput(capability.name(), false, scopeNames(groupRoots), children);
  }

  private static Set<String> scopeNames(Capability cap) {
    return cap.getScopes().stream().map(CapabilityScope::name).collect(Collectors.toSet());
  }

  private static Set<String> scopeNames(List<Capability> capabilities) {
    return capabilities.stream()
        .flatMap(c -> c.getScopes().stream())
        .map(CapabilityScope::name)
        .collect(Collectors.toSet());
  }
}
