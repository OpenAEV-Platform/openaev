package io.openaev.database.model;

/**
 * Entity owned by (and only visible to) a single user.
 *
 * <p>Server-sent events for entities implementing this interface are only delivered to the owning
 * user's stream sessions, bypassing the capability-based permission masking of {@code StreamApi}.
 */
public interface UserScoped {

  /** Returns the id of the user owning this entity. */
  String getOwnerUserId();
}
