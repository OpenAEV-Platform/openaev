package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * Base class for connector entities ({@link Collector}, {@link Injector}, {@link Executor}) that
 * use a static (preset) ID shared across tenants.
 *
 * <p>The DB primary key is composite {@code (id, tenant_id)} for multi-tenant isolation. JPA maps
 * both columns as {@code @Id} via {@code @IdClass(ConnectorEntityId.class)}.
 *
 * <p>Implements {@link Persistable} with a transient {@code newEntity} flag defaulting to {@code
 * false} so that Spring Data JPA uses {@code merge()} by default. Since connector entities have
 * preset IDs, {@code merge()} correctly handles both INSERT (new) and UPDATE (existing) scenarios
 * without requiring explicit {@code setNewEntity()} calls.
 */
@Getter
@Setter
public abstract class BaseConnectorEntity implements Base, Persistable<String> {

  @Transient @JsonIgnore private boolean newEntity = false;

  // -- Contract: subclasses must provide these fields --

  public abstract String getName();

  public abstract void setName(String name);

  public abstract String getType();

  public abstract void setType(String type);

  public abstract boolean isExternal();

  public abstract void setExternal(boolean external);

  /**
   * Returns {@code false} by default — Spring Data uses {@code merge()} which handles both INSERT
   * and UPDATE. Set to {@code true} only if you need to force {@code persist()}.
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
