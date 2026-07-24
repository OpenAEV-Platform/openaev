package io.openaev.database.model;

/** Type of a {@link NotificationTrigger}: live (event driven) or digest (periodic aggregation). */
public enum NotificationTriggerType {
  LIVE,
  DIGEST
}
