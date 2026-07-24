package io.openaev.notification.engine;

import io.openaev.database.model.Notifier;
import io.openaev.database.model.NotifierType;
import java.util.HashMap;
import java.util.Map;

/** Detached, session-safe view of a {@link Notifier} used by the dispatch pipeline. */
public record ResolvedNotifier(
    String id, String name, NotifierType type, Map<String, Object> configuration) {

  public static ResolvedNotifier from(Notifier notifier) {
    return new ResolvedNotifier(
        notifier.getId(),
        notifier.getName(),
        notifier.getType(),
        notifier.getConfiguration() != null
            ? new HashMap<>(notifier.getConfiguration())
            : new HashMap<>());
  }
}
