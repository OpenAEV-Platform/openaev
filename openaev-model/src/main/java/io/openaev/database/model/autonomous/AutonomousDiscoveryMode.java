package io.openaev.database.model.autonomous;

/**
 * How much latitude an agent has to bring newly DISCOVERED entities into an autonomous run - i.e.
 * to create assets, findings, teams or persons on the fly from recon / OSINT results that did not
 * come from an in-scope inject execution.
 *
 * <p>The mode is resolved per acting agent: every entity-creating call an autonomous run makes
 * carries the id of the agent on whose behalf the discovery is being recorded (the orchestrator
 * itself, or a specialist it consulted). OpenAEV looks up that agent's mode and enforces it at the
 * creation choke points ({@code promote-to-asset}, {@code ensure-target-team}, {@code
 * record-discovery}). An unknown / unattributed actor falls back to the safe middle ({@link
 * #SCOPED}).
 */
public enum AutonomousDiscoveryMode {
  /**
   * The agent may only attach findings to assets, teams or persons that ALREADY exist in OpenAEV
   * (and are in scope). It may NOT create any new asset, team or person. Most conservative: recon
   * results only enrich the known perimeter, they never expand it.
   */
  EXISTING_ONLY,

  /**
   * The agent may create new assets / findings / persons, but only WITHIN the run's allow-scope
   * (and never anything on the deny-list). A discovered entity that falls outside the resolved
   * perimeter is rejected. When the run has no allow-list at all there is no perimeter to be
   * inside, so creation is unrestricted (matching OpenAEV's existing "empty allow-list = no
   * restriction" scope semantics).
   */
  SCOPED,

  /**
   * The agent may create new assets / findings / persons anywhere; discovery is allowed to grow the
   * perimeter beyond the initially defined scope. Only the deny-list is still honored.
   */
  EXPANSIVE;

  /**
   * Safe middle-ground default when a tenant / run / actor has expressed no explicit preference.
   */
  public static final AutonomousDiscoveryMode DEFAULT = SCOPED;

  /**
   * Default for the orchestrator itself. The orchestrator stays inside the operator-defined scope
   * and asks the operator to widen it rather than expanding the perimeter on its own, so it never
   * brings in entities beyond what the operator sanctioned. Equal to {@link #DEFAULT} (SCOPED),
   * which is also what {@code resolveDiscoveryMode} falls back to for any unattributed /
   * orchestrator-authored creation.
   */
  public static final AutonomousDiscoveryMode ORCHESTRATOR_DEFAULT = SCOPED;

  /**
   * Default for consulted specialist / additional agents. These agents are recon- and
   * discovery-oriented (OSINT, external surface mapping, ...), so by default they are allowed to
   * bring newly discovered entities into the attack path beyond the initial perimeter (deny-list
   * still wins). Operators can tighten this per agent, per tenant or per run.
   */
  public static final AutonomousDiscoveryMode SPECIALIST_DEFAULT = EXPANSIVE;

  /** Lenient parse: unknown / blank values resolve to {@link #DEFAULT} rather than throwing. */
  public static AutonomousDiscoveryMode fromValue(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    try {
      return valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      return DEFAULT;
    }
  }

  /** True when this mode permits creating a brand-new entity (asset / team / person). */
  public boolean allowsCreation() {
    return this != EXISTING_ONLY;
  }

  /** True when creation must be confined to the run's allow-scope perimeter. */
  public boolean requiresInScope() {
    return this == SCOPED;
  }
}
