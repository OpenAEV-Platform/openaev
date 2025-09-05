package io.openbas.utils;

import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Endpoint;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Triple;

public class InjectorContractUtils {

  /**
   * Builds the complete set of required combinations of TTPs and platform-architecture pairs.
   *
   * @param attackPatterns list of attack patterns (TTPs)
   * @param platforms set of platforms
   * @param architectures set of architecture
   * @return set of (TTP × Platform × Architecture) combinations
   */
  public static Set<Triple<String, Endpoint.PLATFORM_TYPE, String>>
      buildCombinationsAttackPatternPlatformsArchitectures(
          List<AttackPattern> attackPatterns,
          Set<Endpoint.PLATFORM_TYPE> platforms,
          Set<String> architectures) {

    if (attackPatterns == null || platforms == null || architectures == null) {
      return Collections.emptySet();
    }

    return attackPatterns.stream()
        .flatMap(
            attackPattern -> {
              String id = attackPattern.getId();
              return platforms.stream()
                  .flatMap(
                      platform ->
                          architectures.stream()
                              .map(architecture -> Triple.of(id, platform, architecture)));
            })
        .collect(Collectors.toSet());
  }
}
