package io.openaev.processor;

import io.openaev.database.model.Tenant;

/**
 * Common interface for all tenant-scoped migrations and datapacks. Implementations are sorted by
 * their {@link #getSortKey()} (which follows the {@code V{YYYYMMDD}_Description} naming convention)
 * to guarantee chronological execution order regardless of type.
 */
public interface Processable {

  /** Unique identifier used for ordering and idempotency tracking. */
  default String getProcessableId() {
    String canonicalName = this.getClass().getCanonicalName();
    return canonicalName != null ? canonicalName : this.getClass().getName();
  }

  /**
   * Returns the simple class name, used for chronological sorting. The convention {@code
   * V{YYYYMMDD}_Description} ensures correct date-based ordering.
   */
  default String getSortKey() {
    return this.getClass().getSimpleName();
  }

  /** Execute this processable for the given tenant. */
  MigrationProcessingResult process(Tenant tenant);
}
