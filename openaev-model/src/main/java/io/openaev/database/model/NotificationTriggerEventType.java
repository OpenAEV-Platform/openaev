package io.openaev.database.model;

import lombok.RequiredArgsConstructor;

/**
 * Events a live {@link NotificationTrigger} can subscribe to: entity lifecycle operations
 * (create/update/delete) plus the scenario score degradation semantic event (successor of the
 * legacy {@code NotificationRule} DIFFERENCE trigger, only valid for the SCENARIO resource type).
 */
@RequiredArgsConstructor
public enum NotificationTriggerEventType {
  CREATE(null),
  UPDATE(null),
  DELETE(null),
  SCORE_DEGRADATION(ResourceType.SCENARIO),
  SYNC_BLOCKED(ResourceType.CATALOG_SYNC);

  // Resource type the event is limited to; null when every resource type sends it
  private final ResourceType onlyFor;

  public boolean supports(ResourceType resourceType) {
    return onlyFor == null || onlyFor == resourceType;
  }
}
