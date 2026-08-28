package io.openaev.database.model;

/**
 * Events a live {@link NotificationTrigger} can subscribe to: entity lifecycle operations
 * (create/update/delete) plus the scenario score degradation semantic event (successor of the
 * legacy {@code NotificationRule} DIFFERENCE trigger, only valid for the SCENARIO resource type).
 */
public enum NotificationTriggerEventType {
  CREATE,
  UPDATE,
  DELETE,
  SCORE_DEGRADATION
}
