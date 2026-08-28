package io.openaev.database.model;

public enum ScopeRuleSource {
  ASSET,
  ASSET_GROUP,
  TEAM,
  PLAYER,
  MANUAL,
  CSV,
  // Connected tenant security platform frozen at launch (not an allow/deny target). See ADR-006.
  SECURITY_PLATFORM
}
