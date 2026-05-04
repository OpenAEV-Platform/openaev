package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
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
 * <p>Implements {@link Persistable} with a transient {@code newEntity} flag so that Spring Data JPA
 * correctly uses {@code persist()} for new entities and {@code merge()} for existing ones.
 */
@Data
public abstract class BaseConnectorEntity implements Base, Persistable<String> {
  private String id;
  private String name;
  private String type;
  private boolean external;

  @Transient @JsonIgnore private boolean newEntity = true;

  /**
   * Returns {@code true} if this entity has not yet been persisted. Spring Data uses this to decide
   * between {@code persist()} (INSERT) and {@code merge()} (SELECT + UPDATE).
   */
  @Override
  @Transient
  @JsonIgnore
  public boolean isNew() {
    return newEntity;
  }

  /** Mark as persisted after being loaded from the database. */
  @PostLoad
  @PostPersist
  void markNotNew() {
    this.newEntity = false;
  }
}
