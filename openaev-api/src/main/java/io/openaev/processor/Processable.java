package io.openaev.processor;

import io.openaev.database.model.Tenant;

/**
 * Common interface for all tenant-scoped migrations and datapacks. Implementations are sorted by
 * their {@link #getProcessableId()} (which follows the {@code V{YYYYMMDD}_Description} naming
 * convention) to guarantee chronological execution order regardless of type.
 */
public interface Processable {

  /** Unique identifier used for ordering and idempotency tracking. */
  default String getProcessableId() {
    return this.getClass().getCanonicalName();
  }

  /** Execute this processable for the given tenant. */
  MigrationProcessingResult process(Tenant tenant);
}
