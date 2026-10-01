package io.openaev.rest.attack_pattern;

import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Mitigation;
import java.util.Collection;
import org.hibernate.Hibernate;

/**
 * Forces a LAZY {@code attackPatterns} association to load while the tenant scope is still set.
 *
 * <p>{@code attack_patterns} is v2-active, so the scope lives on the transaction. With
 * open-in-view, a collection serialized by Jackson resolves after the handler returned and after
 * that scope is gone, which makes the inspector rewrite the load to match nothing: the response
 * then carries an empty array with a 200, the shape #7026 shipped. Hydrating inside the handler is
 * safe for a collection, which simply excludes rows outside the scope instead of throwing.
 *
 * <p>Injects, scenarios and exercises reach the same association through {@code
 * KillChainPhaseInitializer}, which already walks the attack patterns of every contract.
 */
public final class AttackPatternInitializer {

  private AttackPatternInitializer() {}

  public static void initializeFromContract(InjectorContract injectorContract) {
    Hibernate.initialize(injectorContract.getAttackPatterns());
  }

  public static void initializeFromContracts(Iterable<InjectorContract> injectorContracts) {
    injectorContracts.forEach(AttackPatternInitializer::initializeFromContract);
  }

  public static void initializeFromMitigations(Collection<Mitigation> mitigations) {
    mitigations.forEach(mitigation -> Hibernate.initialize(mitigation.getAttackPatterns()));
  }
}
