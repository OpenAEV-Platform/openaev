package io.openaev.context;

import java.util.List;

/**
 * Carries the effective set of tenant IDs for a given operation (HTTP request or background job).
 *
 * <p>In the simple case (no cross-tenant grants), {@code tenantIds} contains exactly one element —
 * the tenant extracted from the URL path variable.
 *
 * <p>When cross-tenant grants are implemented, {@code tenantIds} will contain the current tenant
 * AND every tenant that has granted access to the current user. The {@link
 * TenantStatementInspector} automatically translates this to {@code WHERE tenant_id IN (...)} in
 * the SQL layer — no service or repository changes are needed.
 *
 * <p>For background jobs, create an {@code OperationState} directly with the target tenant(s):
 *
 * <pre>{@code
 * // Single-tenant job
 * OperationState op = OperationState.of("tenant-abc");
 *
 * // Multi-tenant job (process several tenants in one pass)
 * OperationState op = OperationState.of(List.of("tenant-abc", "tenant-xyz"));
 * }</pre>
 *
 * <p>Never stored in a static field or ThreadLocal — passed explicitly and consumed via {@link
 * TenantExecutionContext#run}.
 */
public record OperationState(List<String> tenantIds) {

  /** Represents an operation with no tenant scope (admin / system context). */
  public static OperationState empty() {
    return new OperationState(List.of());
  }

  /**
   * Creates an OperationState for a single tenant (the common HTTP request case).
   *
   * @param tenantId the tenant identifier extracted from the URL
   */
  public static OperationState of(String tenantId) {
    return new OperationState(tenantId == null ? List.of() : List.of(tenantId));
  }

  /**
   * Creates an OperationState for multiple tenants (grants scenario or multi-tenant jobs).
   *
   * @param tenantIds effective set of tenants the current principal can access
   */
  public static OperationState of(List<String> tenantIds) {
    return new OperationState(tenantIds == null ? List.of() : List.copyOf(tenantIds));
  }

  /**
   * Returns {@code true} if this operation is scoped to at least one tenant. Returns {@code false}
   * for admin/system operations without tenant context.
   */
  public boolean hasTenant() {
    return tenantIds != null && !tenantIds.isEmpty();
  }
}
