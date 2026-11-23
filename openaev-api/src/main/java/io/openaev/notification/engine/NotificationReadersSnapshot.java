package io.openaev.notification.engine;

import java.util.List;

/**
 * The readers of an object captured before a change that removes them, published in the changing
 * transaction and delivered after its commit (OpenCTI evaluates visibility before and after an
 * event, per recipient).
 *
 * <ul>
 *   <li>{@link Kind#DELETION}: a grant-managed root (scenario, simulation, atomic testing) is
 *       deleted. Its grants are deleted with the row, so a check run after commit denies every
 *       grant reader; the readers captured before the delete receive the deleted event.
 *   <li>{@link Kind#ACCESS_LOSS}: a grant is removed or a member leaves a group holding grants. The
 *       captured readers who no longer read the object after commit receive a "no longer visible"
 *       event, of delete type, with the name masked.
 * </ul>
 *
 * @param label the object's name for a deletion, null for an access loss (always masked)
 */
public record NotificationReadersSnapshot(
    Kind kind,
    NotificationResourceCatalog entry,
    String entityId,
    String tenantId,
    String label,
    List<CapturedReaders> triggers) {

  public enum Kind {
    DELETION,
    ACCESS_LOSS
  }

  /** The recipients of one live trigger who read the object before the change. */
  public record CapturedReaders(ResolvedNotificationTrigger trigger, List<String> readers) {

    public CapturedReaders {
      readers = List.copyOf(readers);
    }
  }

  public NotificationReadersSnapshot {
    triggers = List.copyOf(triggers);
  }
}
