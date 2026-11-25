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
  // An approved version the scenario's injects use was rejected, or moved back to pending approval:
  // those injects can no longer run.
  PAYLOAD_REJECTED(ResourceType.SCENARIO),
  PAYLOAD_PENDING_APPROVAL(ResourceType.SCENARIO),
  NEW_PAYLOAD_VERSION(ResourceType.SCENARIO),
  REVOKED(ResourceType.PAYLOAD),
  REVOCATION_LIFTED(ResourceType.PAYLOAD),
  INSTALLED_AUTOMATICALLY(ResourceType.PAYLOAD),
  BASE_UPDATE_AVAILABLE(ResourceType.PAYLOAD),
  RELEASE_RECEIVED(ResourceType.CATALOG),
  // Sent to platform administrators without a trigger: no trigger can watch catalogue syncs.
  SYNC_BLOCKED(ResourceType.CATALOG_SYNC),
  // Payload approval: a version waits for approval, or was rejected.
  APPROVAL_PENDING(ResourceType.PAYLOAD),
  REJECTED(ResourceType.PAYLOAD),
  // No longer sent: kept to read the alerts sent to authors before approval notifications went
  // through triggers. Refused by trigger validation.
  APPROVED(ResourceType.PAYLOAD),
  SUPERSEDED(ResourceType.PAYLOAD);

  private final ResourceType resourceType;

  NotificationTriggerEventType(ResourceType resourceType) {
    this.resourceType = resourceType;
  }

  /** Lifecycle events apply to every resource but the catalogue, whose items only sync. */
  public boolean appliesTo(ResourceType watched) {
    return resourceType == null ? watched != ResourceType.CATALOG : resourceType == watched;
  }
}
