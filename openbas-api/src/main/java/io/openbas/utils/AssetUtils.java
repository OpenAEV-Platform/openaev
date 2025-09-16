package io.openbas.utils;

import io.openbas.database.model.Endpoint;
import io.openbas.injector_contract.ContractTargetedProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
public class AssetUtils {

  /**
   * Build platform-architecture pairs from every endpoint in the list
   *
   * @param endpointSet list of attack patterns (TTPs)
   * @return set of (Platform × Architecture) combinations
   */
  public static Set<Pair<Endpoint.PLATFORM_TYPE, String>> extractPlatformArchPairs(
      List<Endpoint> endpointSet) {
    return endpointSet.stream()
        .map(ep -> Pair.of(ep.getPlatform(), ep.getArch().name()))
        .collect(Collectors.toSet());
  }

  /**
   * Aggregate endpoints by their platform and architecture.
   *
   * @param endpoints the list of endpoints to group
   * @return a map where the key is a string combining platform and architecture, and the value is a
   *     list of endpoints that match that platform-architecture pair
   */
  public static Map<String, List<Endpoint>> mapEndpointsByPlatformArch(List<Endpoint> endpoints) {
    return endpoints.stream()
        .collect(
            Collectors.groupingBy(endpoint -> endpoint.getPlatform() + ":" + endpoint.getArch()));
  }

  /**
   * Get all platform-architecture pairs that are supported by the system.
   *
   * @return a list of all platform-architecture pairs
   */
  public static List<String> getAllPlatform() {
    List<String> allPlatformArchitecturePairs = new ArrayList<>();
    allPlatformArchitecturePairs.add(Endpoint.PLATFORM_TYPE.Linux.name());
    allPlatformArchitecturePairs.add(Endpoint.PLATFORM_TYPE.MacOS.name());
    allPlatformArchitecturePairs.add(Endpoint.PLATFORM_TYPE.Windows.name());
    return allPlatformArchitecturePairs;
  }

  /**
   * Extract target property from an Asset
   *
   * @param endpoint
   * @return Target property: hostname, local IP or Seen IP
   */
  public static ContractTargetedProperty getTargetProperty(Endpoint endpoint) {
    if (endpoint.getHostname() != null && !endpoint.getHostname().isBlank()) {
      return ContractTargetedProperty.hostname;
    } else if (endpoint.getSeenIp() != null && !endpoint.getSeenIp().isBlank()) {
      return ContractTargetedProperty.seen_ip;
    } else {
      return ContractTargetedProperty.local_ip;
    }
  }
}
