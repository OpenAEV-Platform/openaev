package io.openaev.database.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Events a live {@link NotificationTrigger} can subscribe to: entity lifecycle operations
 * (create/update/delete) plus semantic events, each valid for a single resource type.
 */
@RequiredArgsConstructor
public enum NotificationTriggerEventType {
  CREATE(null, "created"),
  UPDATE(null, "updated"),
  DELETE(null, "deleted"),
  SCORE_DEGRADATION(ResourceType.SCENARIO, "score degraded"),
  PAYLOAD_REVOKED(ResourceType.SCENARIO, "payload revoked"),
  NEW_PAYLOAD_VERSION(ResourceType.SCENARIO, "new payload version available"),
  REVOKED(ResourceType.PAYLOAD, "revoked"),
  REVOCATION_LIFTED(ResourceType.PAYLOAD, "revocation lifted"),
  BASE_UPDATE_AVAILABLE(ResourceType.PAYLOAD, "Filigran update available"),
  INSTALLED_AUTOMATICALLY(ResourceType.CATALOG_SYNC, "installed automatically"),
  RELEASE_RECEIVED(ResourceType.CATALOG_SYNC, "release received"),
  SYNC_BLOCKED(ResourceType.CATALOG_SYNC, "sync blocked"),
  /** The attack path of a finished simulation reached an asset of high or very high criticality. */
  CRITICAL_ASSET_REACHED(ResourceType.SIMULATION, "critical asset reached");

  // Resource type the event is limited to; null when every resource type sends it
  private final ResourceType onlyFor;

  // What happened to the entity, as a message tells it
  @Getter private final String label;

  public boolean supports(ResourceType resourceType) {
    return onlyFor == null || onlyFor == resourceType;
  }
}
