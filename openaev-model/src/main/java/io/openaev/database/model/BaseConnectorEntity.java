package io.openaev.database.model;

import lombok.Data;
import org.springframework.data.domain.Persistable;

/**
 * Base class for connector entities ({@link Collector}, {@link Injector}, {@link Executor}) that
 * use a static (preset) ID shared across tenants.
 *
 * <p>The DB primary key is composite {@code (id, tenant_id)} for multi-tenant isolation, but JPA
 * maps only {@code id} as {@code @Id}. The Hibernate tenant filter scopes all queries to the
 * current tenant, and services use {@code findByIdAndTenantId()} for explicit lookups.
 *
 * <p>Implements {@link Persistable} with a transient {@code newEntity} flag so that Spring Data JPA
 * correctly uses {@code persist()} for new entities and {@code merge()} for existing ones.
 */
@Data
public abstract class BaseConnectorEntity implements Base {
  private String id;
  private String name;
  private String type;
  private boolean external;
}
