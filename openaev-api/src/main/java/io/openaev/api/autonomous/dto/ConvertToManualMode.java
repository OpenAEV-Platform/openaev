package io.openaev.api.autonomous.dto;

/** How an autonomous scenario is turned into a manual chained scenario. */
public enum ConvertToManualMode {
  /**
   * Copy the autonomous scenario (metadata + attack-path workflow) into a brand-new manual chained
   * scenario. The original autonomous run is left untouched - safe and reversible.
   */
  DUPLICATE,

  /**
   * Flip THIS scenario to manual: halt the orchestration, drop the autonomous run and its timeline,
   * clear the autonomous flag and keep-alive, and keep the scenario + its simulation as a normal
   * chained scenario/simulation. Irreversible - there is no path back to autonomous mode.
   */
  IN_PLACE,
}
