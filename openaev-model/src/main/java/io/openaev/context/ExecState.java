package io.openaev.context;

import io.openaev.database.model.Tenant;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record ExecState(String currentTenantId, List<String> restrictedTenantIds) {

  public static final Logger logger = LoggerFactory.getLogger(ExecState.class);

  public static ExecState noTenant() {
    return new ExecState(null, List.of());
  }

  /** Single-tenant context: the current tenant is the only accessible tenant. */
  public static ExecState of(String tenant) {
    return new ExecState(tenant, List.of());
  }

  public static ExecState of(String tenant, List<String> accessibleTenants) {
    return new ExecState(tenant, List.copyOf(accessibleTenants));
  }

  /** Convenience: the current tenant's ID, or {@code null} if no tenant is active. */
  public Tenant currentTenant() {
    // Only useful for entity creation, so tenant must be always present
    if (currentTenantId == null) {
      throw new IllegalStateException("No tenant context available");
    }
    return new Tenant(currentTenantId);
  }

  public List<String> restrictedTenantIds() {
    List<@NotBlank String> tenants = new java.util.ArrayList<>(restrictedTenantIds);
    if (currentTenantId != null && !tenants.contains(currentTenantId)) {
      tenants.add(currentTenantId);
    }
    if (tenants.isEmpty()) {
      logger.warn(
          "No tenant restriction applied in ExecState, this may lead to unintended consequences");
    }
    return tenants;
  }
}
