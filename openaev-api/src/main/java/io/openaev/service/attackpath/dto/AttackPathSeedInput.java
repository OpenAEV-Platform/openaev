package io.openaev.service.attackpath.dto;

import io.openaev.service.attackpath.AttackPathSeedParams;

/**
 * Request body for {@code POST /seed} (issue 6647): a preset ({@code smoke} or {@code full}) and a
 * seed integer for reproducibility. Both optional; defaults keep the endpoint callable with an
 * empty body for a quick smoke run.
 */
public record AttackPathSeedInput(String preset, Long seed) {

  public AttackPathSeedParams toParams() {
    long resolvedSeed = seed != null ? seed : 1L;
    return "full".equalsIgnoreCase(preset)
        ? AttackPathSeedParams.full(resolvedSeed)
        : AttackPathSeedParams.smoke(resolvedSeed);
  }
}
