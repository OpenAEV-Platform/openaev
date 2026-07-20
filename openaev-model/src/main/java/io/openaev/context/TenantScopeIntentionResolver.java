package io.openaev.context;

import io.openaev.database.repository.TenantRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves a scope intention into an explicit scope. {@link TxCtx.AllTenants} becomes a {@link
 * TxCtx.Restricted} over the currently active tenants; explicit scopes pass through untouched. The
 * resolution happens inside the opening transaction, so a long-running job re-resolves at every
 * short transaction and naturally sees tenants created in between.
 *
 * <p>The tenants registry is read outside any tenant scope by design: the {@code tenants} table
 * carries no {@code tenant_id} column and is never rewritten by the statement inspector, so there
 * is no chicken-and-egg between resolving the scope and setting it.
 *
 * <p>Only the system/job flavour is wired (active tenants). A caller-bound flavour (a user's
 * memberships) will plug through {@link CallerScopeSource} when background work runs on behalf of a
 * user; it is deliberately not wired yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenantScopeIntentionResolver {

  private final TenantRepository tenantRepository;

  /** The future caller-bound resolution source (user memberships). Not wired in the POC. */
  public interface CallerScopeSource {
    List<String> tenantIdsForCaller();
  }

  TxCtx resolve(TxCtx ctx) {
    if (ctx instanceof TxCtx.Missing) {
      throw new IllegalArgumentException("a Missing scope cannot be resolved: carry a real scope");
    }
    if (!(ctx instanceof TxCtx.AllTenants)) {
      return ctx;
    }
    return TxCtx.forTenants(activeTenantIds());
  }

  /**
   * The currently active tenant ids, read from the unscoped registry. Backs both the {@code
   * allTenants()} resolution and the primitive's per-tenant loop. Package-private to keep the
   * resolution machinery internal; enumeration itself stays public through the tenant registry,
   * which parallel per-tenant jobs legitimately use for their own fan-out.
   */
  List<String> activeTenantIds() {
    List<String> activeTenantIds = tenantRepository.findAllIdsByDeletedAtIsNull();
    if (activeTenantIds.isEmpty()) {
      throw new IllegalStateException(
          "no active tenant in the registry: cannot resolve a tenant scope");
    }
    // The first support question during a conversion is "what did the job actually see?".
    log.debug("Resolved allTenants() to {} active tenant(s)", activeTenantIds.size());
    return activeTenantIds;
  }
}
