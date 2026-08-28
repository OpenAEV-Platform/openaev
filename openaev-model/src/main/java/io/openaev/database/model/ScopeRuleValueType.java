package io.openaev.database.model;

public enum ScopeRuleValueType {
  IP,
  IP_SUBNET,
  DOMAIN,
  ASSET_ID,
  ASSET_GROUP_ID,
  TEAM_ID,
  PLAYER_ID,
  // Never collected by the allow/deny resolution (PrimitiveValidationContextBuilder), which keeps
  // SECURITY_PLATFORM rows out of execution targeting. See ADR-006.
  SECURITY_PLATFORM_ID
}
