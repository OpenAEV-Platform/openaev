package io.openaev.database.model;

/**
 * Logical groups used to organize capabilities in the UI tree. Declaration order drives the display
 * order of the groups.
 */
public enum CapabilityGroup {
  SUPERUSER,
  DASHBOARDS,
  REPORTINGS,
  FINDINGS,
  ASSESSMENT,
  THREAT_ARSENALS,
  CREDENTIALS,
  TARGETS,
  CONTENT,
  PLATFORM_SETTINGS,
  TENANT_SETTINGS,
  SECURITY,
  TAXONOMY,
  TENANTS,
  STIX,
  SERVICE
}
