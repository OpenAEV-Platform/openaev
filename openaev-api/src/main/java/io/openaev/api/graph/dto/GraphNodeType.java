package io.openaev.api.graph.dto;

/**
 * The entity kinds the exploration graph (#9388) draws. Each one maps to a screen of the platform
 * the node detail panel deep-links to. Persons, organizations and teams (#9447) are the people side
 * of the estate: the person behind an identity asset, the organization it works for, its teams.
 */
public enum GraphNodeType {
  ASSET,
  ASSET_GROUP,
  FINDING,
  INJECT,
  SIMULATION,
  SCENARIO,
  PERSON,
  ORGANIZATION,
  TEAM;

  /** Parses the path segment of the graph endpoints, case-insensitive ({@code asset_group}). */
  public static GraphNodeType fromParam(String value) {
    if (value == null) {
      throw new IllegalArgumentException("entity type is required");
    }
    return GraphNodeType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
  }
}
