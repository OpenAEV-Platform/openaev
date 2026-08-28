package io.openaev.database.model;

/**
 * Business criticality of an {@link Asset}, used to prioritize exposure and risk - a cross-cutting
 * concept shared by every exposure-management platform (Microsoft Very High/High/Medium/Low,
 * Axonius, Tenable, ...).
 */
public enum AssetCriticality {
  VERY_HIGH,
  HIGH,
  MEDIUM,
  LOW,
  UNKNOWN;
}
