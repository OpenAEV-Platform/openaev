package io.openbas.utils;

import io.openbas.database.model.Endpoint;
import java.util.List;
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
  public static Set<Pair<Endpoint.PLATFORM_TYPE, String>> computePairsPlatformArchitecture(
      List<Endpoint> endpointList) {
    return endpointList.stream()
        .map(ep -> Pair.of(ep.getPlatform(), ep.getArch().name()))
        .collect(Collectors.toSet());
  }
}
