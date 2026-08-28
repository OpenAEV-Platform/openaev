package io.openaev.context;

import java.util.Collection;
import java.util.List;

/**
 * Tenant scope carried by a database transaction.
 *
 * <p>Three states, never {@code null}: {@link Missing} (no scope, access denied), {@link
 * Restricted} (an explicit, non-empty set of tenants), and {@link AllTenants} (an unresolved
 * intention for genuinely-global background work). No wildcard ever reaches the scope channel:
 * {@link AllTenants} cannot serialize itself, it is resolved into an explicit {@link Restricted}
 * list at scope-set time, so {@code can_access_tenant} only ever sees explicit tenant ids.
 *
 * <p>{@link #toGuc()} serializes the scope for {@code set_config('app.current_tenants', …, true)},
 * read back by the {@code can_access_tenant(tenant_id)} SQL function.
 */
public sealed interface TxCtx permits TxCtx.Missing, TxCtx.Restricted, TxCtx.AllTenants {

  /** Value for {@code set_config('app.current_tenants', …, true)}; never {@code null}. */
  String toGuc();

  /** No scope: access is denied. */
  static TxCtx missing() {
    return Missing.INSTANCE;
  }

  /** Scope restricted to a single tenant. */
  static TxCtx forTenant(String tenantId) {
    return new Restricted(List.of(tenantId));
  }

  /** Scope restricted to a non-empty set of tenants. */
  static TxCtx forTenants(Collection<String> tenantIds) {
    return new Restricted(List.copyOf(tenantIds));
  }

  /**
   * The intention "this work must see every tenant" (genuinely-global background work, e.g. the ES
   * indexing sweep). Not a wildcard: it is resolved into an explicit {@link Restricted} list of
   * active tenant ids when the scope is set, and only {@code TenantScopedTransaction} does that.
   */
  static TxCtx allTenants() {
    return AllTenants.INSTANCE;
  }

  record Missing() implements TxCtx {
    static final Missing INSTANCE = new Missing();

    /** Empty string denies all rows and overrides any configured default. */
    @Override
    public String toGuc() {
      return "";
    }
  }

  /** An unresolved intention: it cannot reach the scope channel, only its resolution can. */
  record AllTenants() implements TxCtx {
    static final AllTenants INSTANCE = new AllTenants();

    @Override
    public String toGuc() {
      throw new IllegalStateException(
          "allTenants() is an unresolved intention: it cannot be serialized to the scope channel."
              + " Only TenantScopedTransaction resolves it into an explicit tenant list; it is not"
              + " usable on the HTTP path.");
    }
  }

  record Restricted(List<String> tenantIds) implements TxCtx {
    public Restricted {
      tenantIds = List.copyOf(tenantIds);
      if (tenantIds.isEmpty()) {
        throw new IllegalArgumentException("tenant scope must not be empty; use missing() instead");
      }
      for (String id : tenantIds) {
        if (id.isBlank()) {
          throw new IllegalArgumentException("tenant id must not be blank");
        }
        if (id.indexOf(',') >= 0) {
          throw new IllegalArgumentException("tenant id must not contain ','");
        }
      }
    }

    @Override
    public String toGuc() {
      return String.join(",", tenantIds);
    }
  }
}
