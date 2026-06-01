package io.openaev.integration;

/**
 * Marker interface for any built-in component (injector, executor, collector) that must be
 * registered once per tenant. Implementations are auto-discovered by {@link ManagerFactory} via
 * Spring injection, so adding a new built-in component requires only implementing this interface on
 * a {@code @Service} bean — no manual wiring in {@code ManagerFactory}.
 *
 * <p>Must be idempotent — safe to call even if the component already exists (upsert semantics).
 */
public interface BuiltinTenantRegistrable {

  /** Registers this built-in component for the given tenant. */
  void registerForTenant(String tenantId) throws Exception;

  /**
   * Cleans up any in-memory state for the given tenant (caches, schedulers, etc.). DB rows are
   * typically deleted by CASCADE on tenant removal, so this method only needs to handle non-DB
   * resources. Default implementation is a no-op.
   */
  default void unregisterForTenant(String tenantId) {}
}
