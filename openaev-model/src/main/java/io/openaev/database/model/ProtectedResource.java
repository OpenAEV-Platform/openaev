package io.openaev.database.model;

/**
 * Contract for resources whose mutability is restricted by business rules.
 *
 * <p>The protected state may be persisted (for example {@code protectedDefinition}) or computed
 * from the resource identity/state (for example the default tenant).
 */
public interface ProtectedResource {

  boolean isProtectedResource();
}
