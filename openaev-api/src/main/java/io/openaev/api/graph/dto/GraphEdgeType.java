package io.openaev.api.graph.dto;

/** The relation kinds of the exploration graph (#9388), one per link the UI can expand by. */
public enum GraphEdgeType {
  /** Asset group contains asset. */
  MEMBER_OF,
  /** Finding observed on asset. */
  FOUND_ON,
  /** Inject produced finding. */
  PRODUCED,
  /** Inject targeted asset or asset group, with its expectation result. */
  TARGETS,
  /** Inject belongs to a simulation. */
  PART_OF,
  /** Simulation is a run of a scenario. */
  RUN_OF,
  /** Identity asset belongs to a person ({@code asset_linked_person}). */
  IDENTITY_OF,
  /** Person works for an organization ({@code users_organizations}). */
  WORKS_FOR,
  /** Person is a member of a team ({@code users_teams}). */
  IN_TEAM,
  /** Team belongs to an organization ({@code team_organization}). */
  TEAM_OF,
  /** Asset is linked to another asset ({@code assets_links}, typed: uses, runs on, ...). */
  LINKED_TO,
  /**
   * Attack path step: an attack-path execution of a simulation went from the source asset to the
   * target asset ({@code attackpath_execution}), with its worst prevention / detection verdict.
   */
  ATTACK_STEP
}
