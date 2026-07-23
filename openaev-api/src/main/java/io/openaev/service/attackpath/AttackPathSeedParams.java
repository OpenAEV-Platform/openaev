package io.openaev.service.attackpath;

import java.util.List;

/**
 * Parameters for the attack-path seed generator (issue 6647). Deterministic given {@code seed}: the
 * generator drives every choice from a single {@link java.util.Random} seeded with it, so the same
 * parameters produce the same row counts and shape.
 *
 * <p>{@code outlierSizes} are the deliberately large single-simulation sizes (a real credential
 * spray shape), assigned to the first simulations; the rest vary around {@code typicalExecutions}.
 * This is the skewed distribution the POC needs, because read and render cost are per-simulation,
 * not per-total.
 */
public record AttackPathSeedParams(
    int simulations,
    int tenants,
    int typicalExecutions,
    int endpointsPerSimulation,
    int injectorsPerSimulation,
    int findingsPerEndpoint,
    double sharedFindingRatio,
    double discoveredEndpointRatio,
    double preventedRatio,
    List<Integer> outlierSizes,
    long seed) {

  /**
   * Small dataset for fast local iteration and the smoke test; one clear outlier over the median.
   */
  public static AttackPathSeedParams smoke(long seed) {
    return new AttackPathSeedParams(6, 2, 20, 5, 3, 3, 0.3, 0.25, 0.67, List.of(200), seed);
  }

  /**
   * ~100-endpoint single simulation: the mid navigation dataset between the tiny demo and the
   * benchmark sizes, to feel pan/zoom/expand on a real-but-navigable graph.
   */
  public static AttackPathSeedParams navigation(long seed) {
    return new AttackPathSeedParams(1, 1, 3000, 100, 8, 4, 0.4, 0.15, 0.7, List.of(), seed);
  }

  /** Mid dataset (~0.5M, one ~100k outlier) to validate the benchmark without the full run. */
  public static AttackPathSeedParams medium(long seed) {
    return new AttackPathSeedParams(
        20, 2, 20000, 50, 20, 4, 0.4, 0.15, 0.7, List.of(100_000), seed);
  }

  /** Intermediate dataset (~2M) — the middle point for the volume-scaling comparison. */
  public static AttackPathSeedParams large(long seed) {
    return new AttackPathSeedParams(
        80, 4, 25000, 50, 20, 4, 0.4, 0.15, 0.7, List.of(100_000), seed);
  }

  /**
   * Headline dataset: at least 5,000,000 executions across ~200 simulations and a few tenants, plus
   * the ~100k / ~300k / ~500k single-simulation outliers. Used by the benchmark harness, not the
   * smoke test.
   */
  public static AttackPathSeedParams full(long seed) {
    return new AttackPathSeedParams(
        200, 4, 25000, 50, 20, 4, 0.4, 0.15, 0.7, List.of(100_000, 300_000, 500_000), seed);
  }
}
