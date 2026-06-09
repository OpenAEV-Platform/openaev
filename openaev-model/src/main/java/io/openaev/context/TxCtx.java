package io.openaev.context;

import io.openaev.database.model.Tenant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record TxCtx(String tenantIdFromUri, List<String> tenantIds) {

  public static final Logger logger = LoggerFactory.getLogger(TxCtx.class);

  public static TxCtx noTenant() {
    return new TxCtx(null, List.of());
  }

  /** Single-tenant context: the current tenant is the only accessible tenant. */
  public static TxCtx of(String tenantFromUri) {
    return new TxCtx(tenantFromUri, List.of());
  }

  public static TxCtx of(String tenantFromUri, List<String> tenantIds) {
    return new TxCtx(tenantFromUri, List.copyOf(tenantIds));
  }

  /** Convenience: the current tenant's ID, or {@code null} if no tenant is active. */
  @Deprecated
  public Tenant tenantFromUri() {
    // Only useful for entity creation, so tenant must be always present
    if (tenantIdFromUri == null) {
      throw new IllegalStateException("No tenant context available");
    }
    return new Tenant(tenantIdFromUri);
  }

  // public List<String> tenantIds() {
  //     List<@NotBlank String> tenants = new java.util.ArrayList<>(restrictedTenantIds);
  //     if (currentTenantId != null && !tenants.contains(currentTenantId)) {
  //         tenants.add(currentTenantId);
  //     }
  //     if (tenants.isEmpty()) {
  //         logger.warn(
  //                 "No tenant restriction applied in ExecState, this may lead to unintended
  // consequences");
  //     }
  //     return tenants;
  // }
}
