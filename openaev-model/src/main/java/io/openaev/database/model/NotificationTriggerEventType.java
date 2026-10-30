package io.openaev.database.model;

/**
 * Events a live {@link NotificationTrigger} can subscribe to: entity lifecycle operations
 * (create/update/delete) plus semantic events, each valid for a single resource type.
 */
public enum NotificationTriggerEventType {
  CREATE(null),
  UPDATE(null),
  DELETE(null),
  SCORE_DEGRADATION(ResourceType.SCENARIO),
  PAYLOAD_REVOKED(ResourceType.SCENARIO),
  NEW_PAYLOAD_VERSION(ResourceType.SCENARIO),
  REVOKED(ResourceType.PAYLOAD),
  REVOCATION_LIFTED(ResourceType.PAYLOAD),
  INSTALLED_AUTOMATICALLY(ResourceType.PAYLOAD),
  BASE_UPDATE_AVAILABLE(ResourceType.PAYLOAD),
  RELEASE_RECEIVED(ResourceType.CATALOG),
  // Sent to platform administrators without a trigger: no trigger can watch catalogue syncs.
  SYNC_BLOCKED(ResourceType.CATALOG_SYNC);

  private final ResourceType resourceType;

  NotificationTriggerEventType(ResourceType resourceType) {
    this.resourceType = resourceType;
  }

  /** Lifecycle events apply to every resource but the catalogue, whose items only sync. */
  public boolean appliesTo(ResourceType watched) {
    return resourceType == null ? watched != ResourceType.CATALOG : resourceType == watched;
  }
}
