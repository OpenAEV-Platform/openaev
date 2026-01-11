package io.openaev.utils;

/**
 * Enumeration representing the different types of targets that can be used for inject execution.
 *
 * <p>Targets determine who or what receives the inject during simulation exercises or atomic
 * testing. Each target type represents a specific category of entities in the OpenAEV platform.
 *
 * @see io.openaev.database.model.Inject
 * @see io.openaev.database.model.InjectorContract
 */
public enum TargetType {

  /** A single agent entity. */
  AGENT,

  /** Multiple agent entities. */
  AGENTS,

  /** Asset entities (endpoints, systems, or devices). */
  ASSETS,

  /** Groups of assets organized for collective targeting. */
  ASSETS_GROUPS,

  /** Individual players participating in exercises. */
  PLAYERS,

  /** Teams of players organized for collective participation. */
  TEAMS,

  /** Endpoint devices in the infrastructure. */
  ENDPOINTS
}
