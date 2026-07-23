package io.openaev.context;

import java.util.function.Supplier;

/**
 * Thread-local marker for code running inside a massive (bulk) operation.
 *
 * <p>While active, every {@link io.openaev.database.audit.BaseEvent} constructed on the thread is
 * flagged as not listened, so the SSE stream ({@code StreamApi}) does not broadcast one event per
 * mutated entity to every connected browser — a bulk deletion used to hammer each client with
 * thousands of per-entity delete events, forcing dashboards to refresh once per delete. Consumers
 * are notified through aggregated {@code bulk-operation} progress events instead, and refresh once
 * on completion.
 *
 * <p>Only the frontend stream is affected: search-engine indexing rides the separate {@link
 * io.openaev.database.audit.IndexEvent} pipeline and audit logging reads the entity snapshots
 * directly, so both remain untouched by the suppression.
 *
 * <p>The flag must wrap the whole transaction (including its commit): entity lifecycle callbacks
 * fire during the final flush at commit time, on the same thread.
 */
public final class BulkOperationContext {

  private static final ThreadLocal<Boolean> SUPPRESS_STREAM_EVENTS = new ThreadLocal<>();

  private BulkOperationContext() {}

  /** Whether the current thread is running inside a bulk operation. */
  public static boolean isActive() {
    return Boolean.TRUE.equals(SUPPRESS_STREAM_EVENTS.get());
  }

  /**
   * Runs the given work with per-entity stream events suppressed. Must wrap the transaction
   * boundary (proxy call), not just the transactional work, so events fired by the commit-time
   * flush are covered too.
   */
  public static <T> T runSuppressed(Supplier<T> work) {
    boolean alreadyActive = isActive();
    SUPPRESS_STREAM_EVENTS.set(Boolean.TRUE);
    try {
      return work.get();
    } finally {
      if (alreadyActive) {
        SUPPRESS_STREAM_EVENTS.set(Boolean.TRUE);
      } else {
        SUPPRESS_STREAM_EVENTS.remove();
      }
    }
  }
}
