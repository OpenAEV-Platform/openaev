package io.openaev.service.attackpath.dto;

import io.openaev.service.attackpath.AttackPathSeedParams;
import java.util.Locale;

/**
 * Request body for {@code POST /seed} (issue 6647): a preset ({@code smoke}, {@code nav}, {@code
 * medium}, {@code large} or {@code full}), a seed integer for reproducibility, and an optional
 * {@code tenantId}. All optional; an empty body runs a quick smoke seed under synthetic tenants.
 *
 * <p>{@code tenantId} makes the seed visible: when set, every row is written under that existing
 * tenant (no synthetic tenants), so the seeded simulations show up in the front for that tenant.
 * When null the generator creates its own synthetic tenants (what the benchmark uses).
 */
public record AttackPathSeedInput(String preset, Long seed, String tenantId) {

  public AttackPathSeedParams toParams() {
    long resolvedSeed = seed != null ? seed : 1L;
    return switch (preset == null ? "smoke" : preset.toLowerCase(Locale.ROOT)) {
      case "full" -> AttackPathSeedParams.full(resolvedSeed);
      case "large" -> AttackPathSeedParams.large(resolvedSeed);
      case "medium" -> AttackPathSeedParams.medium(resolvedSeed);
      case "nav" -> AttackPathSeedParams.navigation(resolvedSeed);
      default -> AttackPathSeedParams.smoke(resolvedSeed);
    };
  }
}
