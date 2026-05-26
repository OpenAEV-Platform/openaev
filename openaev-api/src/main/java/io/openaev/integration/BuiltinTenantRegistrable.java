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

  /** Registers this built-in component in the <b>current</b> tenant context. */
  void registerForTenant() throws Exception;
}
