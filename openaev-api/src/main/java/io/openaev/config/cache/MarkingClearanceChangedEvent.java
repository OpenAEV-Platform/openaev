package io.openaev.config.cache;

import java.util.Set;

/**
 * The marking clearance of these users changed: what they display may no longer match what they may
 * read. Published by {@link MarkingClearanceCacheManager} on every eviction, the one place every
 * clearance change goes through.
 *
 * @param userIds the users whose clearance changed; ignored when {@code allUsers}
 * @param allUsers whether every user's clearance may have changed
 */
public record MarkingClearanceChangedEvent(Set<String> userIds, boolean allUsers) {

  public static MarkingClearanceChangedEvent of(Set<String> userIds) {
    return new MarkingClearanceChangedEvent(Set.copyOf(userIds), false);
  }

  public static MarkingClearanceChangedEvent everyone() {
    return new MarkingClearanceChangedEvent(Set.of(), true);
  }

  public boolean concerns(String userId) {
    return allUsers || userIds.contains(userId);
  }
}
