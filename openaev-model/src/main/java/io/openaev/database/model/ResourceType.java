package io.openaev.database.model;

import jakarta.validation.constraints.NotNull;

public enum ResourceType {
  ASSET,
  AGENT,
  SCENARIO,
  SIMULATION,
  PLAYER,
  USER,
  TEAM,
  ATOMIC_TESTING,
  NOTIFICATION_RULE,
  PAYLOAD,
  RESOURCE_TYPE,
  SECURITY_PLATFORM,
  DOCUMENT,
  CHANNEL,
  FINDING,
  DASHBOARD,
  PLATFORM_SETTING,
  LESSON_LEARNED,
  CHALLENGE,
  INJECT,
  JOB,
  TAG,
  TAG_RULE,
  KILL_CHAIN_PHASE,
  ATTACK_PATTERN,
  ASSET_GROUP,
  VULNERABILITY,
  USER_GROUP,
  INJECTOR,
  INJECTOR_CONTRACT,
  MAPPER,
  GROUP_ROLE,
  ORGANIZATION,
  COLLECTOR,
  STIX_BUNDLE,
  OBJECTIVE,
  EVALUATION,
  CATALOG,
  // Special resource types
  UNKNOWN,
  SIMULATION_OR_SCENARIO, // Used to represent either a simulation or a scenario.
  SKIP_RBAC; // Used to skip RBAC checks.

  public static ResourceType fromString(@NotNull String name) {
    try {
      if ("CVE".equals(name)) {
        return VULNERABILITY;
      }
      return ResourceType.valueOf(name.toUpperCase());
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
