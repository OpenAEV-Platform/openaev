package io.openaev.database.model;

import com.fasterxml.jackson.databind.DeserializationContext;
import io.openaev.context.TenantContext;
import io.openaev.helper.CompositeIdResolvableI;
import lombok.Data;

/**
 * Base class for connector entities ({@link Collector}, {@link Injector}, {@link Executor}) that
 * ship with a preset business id shared across tenants.
 *
 * <p>The primary key is composite {@code (id, tenant_id)} for multi-tenant isolation, mapped as an
 * {@code @IdClass(ConnectorCompositeId)} on each subclass. The Hibernate tenant filter scopes all
 * queries to the current tenant, and services use {@code findByIdAndTenantId()} for explicit
 * lookups.
 *
 * <p>Implements {@link CompositeIdResolvableI} so a JSON reference carrying only the business id is
 * deserialized into the full composite id for the current tenant.
 */
@Data
public abstract class BaseConnectorEntity implements Base, CompositeIdResolvableI {
  private String id;
  private String name;
  private String type;
  private boolean external;

  @Override
  public Object resolveCompositeId(String rawId, DeserializationContext ctxt) {
    return new ConnectorCompositeId(rawId, TenantContext.getCurrentTenant());
  }
}
