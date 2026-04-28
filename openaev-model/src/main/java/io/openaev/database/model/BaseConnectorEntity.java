package io.openaev.database.model;

import jakarta.persistence.Transient;
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
 * <p>Implements {@link Persistable} so that Spring Data JPA knows these entities always have
 * assigned IDs and should use {@code merge()} instead of {@code persist()}.
 */
@Data
public abstract class BaseConnectorEntity implements Base, Persistable<String> {
  private String id;
  private String name;
  private String type;
  private boolean external;

  /**
   * Connector entities always have an assigned (static) ID, so they are never "new" from JPA's
   * perspective. This ensures Spring Data uses {@code merge()} rather than {@code persist()}.
   */
  @Override
  @Transient
  public boolean isNew() {
    return false;
  }
}
