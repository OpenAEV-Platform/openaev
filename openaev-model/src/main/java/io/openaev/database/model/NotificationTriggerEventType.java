package io.openaev.database.model;

import java.util.Set;
import lombok.Getter;

/**
 * Events a live {@link NotificationTrigger} can subscribe to: entity lifecycle operations
 * (create/update/delete) plus semantic events, each valid for a single resource type.
 */
public enum NotificationTriggerEventType {
  CREATE("created"),
  UPDATE("updated"),
  DELETE("deleted"),
  SCORE_DEGRADATION("score degraded", ResourceType.SCENARIO),
  PAYLOAD_REVOKED("payload revoked", ResourceType.SCENARIO),
  NEW_PAYLOAD_VERSION("new payload version available", ResourceType.SCENARIO),
  REVOKED("revoked", ResourceType.PAYLOAD, ResourceType.SCENARIO),
  REVOCATION_LIFTED("revocation lifted", ResourceType.PAYLOAD),
  BASE_UPDATE_AVAILABLE("Filigran update available", ResourceType.PAYLOAD),
  INSTALLED_AUTOMATICALLY("installed automatically", ResourceType.CATALOG_SYNC),
  RELEASE_RECEIVED("release received", ResourceType.CATALOG_SYNC),
  SYNC_BLOCKED("sync blocked", ResourceType.CATALOG_SYNC),
  /** The attack path of a finished simulation reached an asset of high or very high criticality. */
  CRITICAL_ASSET_REACHED("critical asset reached", ResourceType.SIMULATION);

  // What happened to the entity, as a message tells it
  @Getter private final String label;

  // Resource types the event is limited to; empty when every resource type sends it
  private final Set<ResourceType> onlyFor;

  NotificationTriggerEventType(String label, ResourceType... onlyFor) {
    this.label = label;
    this.onlyFor = Set.of(onlyFor);
  }

  public boolean supports(ResourceType resourceType) {
    return onlyFor.isEmpty() || onlyFor.contains(resourceType);
  }
}
