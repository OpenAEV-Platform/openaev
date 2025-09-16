package io.openbas.utils;

import io.openbas.database.model.Endpoint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class AssetUtils {

  /**
   * Build platform-architecture pairs from every endpoint in the list
   *
   * @param endpointList list of attack patterns (TTPs)
   * @return set of (Platform × Architecture) combinations
   */
  public static Set<Pair<Endpoint.PLATFORM_TYPE, String>> extractPlatformArchPairs(
      List<Endpoint> endpointList) {
    return endpointList.stream()
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
}
