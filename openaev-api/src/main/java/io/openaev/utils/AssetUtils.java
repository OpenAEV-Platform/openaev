package io.openaev.utils;

import io.openaev.database.model.Endpoint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Utility class for asset-related operations, primarily focused on endpoint management.
 *
 * <p>Provides helper methods for grouping and extracting platform and architecture information from
 * endpoints. These utilities are commonly used when filtering or organizing assets for inject
 * execution based on their system characteristics.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Endpoint
 * @see io.openaev.database.model.Asset
 */
public class AssetUtils {

  private AssetUtils() {}

  /**
   * Extracts unique platform-architecture pairs from a list of endpoints.
   *
   * <p>Creates a set of tuples combining each endpoint's platform type with its architecture,
   * useful for determining what platform combinations exist in a group of assets.
   *
   * @param endpointList the list of endpoints to process
   * @return a set of (Platform, Architecture) pairs representing all unique combinations
   */
  public static Set<Pair<Endpoint.PLATFORM_TYPE, String>> extractPlatformArchPairs(
      List<Endpoint> endpointList) {
    return endpointList.stream()
        .map(ep -> Pair.of(ep.getPlatform(), ep.getArch().name()))
        .collect(Collectors.toSet());
  }

  /**
   * Groups endpoints by their platform and architecture combination.
   *
   * <p>The grouping key is formatted as "{platform}:{architecture}" (e.g., "Windows:x86_64"). This
   * is useful for batch operations that need to process endpoints with similar system
   * characteristics together.
   *
   * @param endpoints the list of endpoints to group
   * @return a map where keys are "platform:architecture" strings and values are lists of matching
   *     endpoints
   */
  public static Map<String, List<Endpoint>> mapEndpointsByPlatformArch(List<Endpoint> endpoints) {
    return endpoints.stream()
        .collect(
            Collectors.groupingBy(endpoint -> endpoint.getPlatform() + ":" + endpoint.getArch()));
  }
}
