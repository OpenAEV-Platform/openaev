package io.openaev.rest.kill_chain_phase;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import java.util.Collection;
import org.hibernate.Hibernate;

/**
 * Forces the LAZY {@code AttackPattern#killChainPhases} association to load while the tenant scope
 * is still set.
 */
public final class KillChainPhaseInitializer {

  private KillChainPhaseInitializer() {}

  private static void initialize(AttackPattern attackPattern) {
    Hibernate.initialize(attackPattern.getKillChainPhases());
  }

  public static void initializeFromContract(InjectorContract injectorContract) {
    injectorContract.getAttackPatterns().forEach(KillChainPhaseInitializer::initialize);
  }

  /**
   * Hydrates every phase reachable from a set of injects (the {@code *_kill_chain_phases} path).
   */
  public static void initializeFromInjects(Collection<Inject> injects) {
    injects.stream()
        .map(Inject::getInjectorContract)
        .flatMap(java.util.Optional::stream)
        .forEach(KillChainPhaseInitializer::initializeFromContract);
  }
}
