package io.openaev.context;

import io.openaev.database.model.Tenant;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Immutable execution context that carries the resolved current {@link Tenant} and the full list of
 * accessible tenants for the current operation (HTTP request or background job).
 *
 * <h3>Current tenant</h3>
 *
 * <p>The {@code tenant} field is the <em>active</em> tenant — the one entities are created under
 * (via {@code entity.setTenant(state.tenant())}). It is resolved once at the entry point
 * (interceptor / argument resolver / job scheduler) and propagated through the call chain.
 *
 * <h3>Accessible tenants</h3>
 *
 * <p>{@code accessibleTenants} contains every tenant the user is allowed to <em>read</em> from,
 * including the current one. The {@link io.openaev.config.TenantStatementInspector} translates this
 * to {@code WHERE tenant_id IN (...)} in the SQL layer — no service or repository changes needed.
 *
 * <p>Today, {@code accessibleTenants} is always {@code List.of(tenant)} (single-tenant). When
 * cross-tenant grants are implemented, the interceptor will query the grants table and populate
 * this list with additional tenants.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * // HTTP request — resolved automatically by TenantInterceptor + OperationStateArgumentResolver
 * @GetMapping("/api/tenants/{tenantId}/documents")
 * public List<Document> documents(ExecState state) {
 *     // state.tenant() → resolved Tenant entity
 *     // state.accessibleTenantIds() → ["tenant-abc"] (used by SQL interceptor)
 * }
 *
 * // Background job
 * ExecState state = ExecState.of(tenantRepository.findById(tenantId).orElseThrow());
 * TenantExecutionContext.run(state, () -> processForTenant(state));
 *
 * // Entity creation — use the resolved Tenant directly
 * myEntity.setTenant(state.tenant());
 * }</pre>
 */
public record ExecState(String currentTenantId, List<String> accessibleTenantIds) {

  /** Single-tenant context: the current tenant is the only accessible tenant. */
  public static ExecState of(String tenant) {
    return new ExecState(tenant, tenant == null ? List.of() : List.of(tenant));
  }

  public static ExecState of(String currentTenant, List<String> accessibleTenants) {
    return new ExecState(
        currentTenant, accessibleTenants == null ? List.of() : List.copyOf(accessibleTenants));
  }

  /** Convenience: the current tenant's ID, or {@code null} if no tenant is active. */
  public Tenant currentTenant() {
    return new Tenant(currentTenantId);
  }

  public List<String> accessibleTenantIds() {
    List<@NotBlank String> tenants = new java.util.ArrayList<>(accessibleTenantIds);
    tenants.add(currentTenantId);
    return tenants;
  }
}
