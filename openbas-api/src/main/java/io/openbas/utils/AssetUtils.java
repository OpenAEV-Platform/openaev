package io.openbas.utils;

import io.openbas.database.model.Endpoint;
import io.openbas.injector_contract.ContractTargetedProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
