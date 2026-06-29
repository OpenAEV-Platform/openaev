package io.openaev.context;

import java.util.Collection;
import java.util.List;

/**
 * Tenant scope carried by a database transaction.
 *
 * <p>Two states, never {@code null}: {@link Missing} (no scope, access denied) and {@link
 * Restricted} (an explicit, non-empty set of tenants). There is no "all tenants" state: a wildcard
 * in the scope channel would silently widen access.
 *
 * <p>{@link #toGuc()} serializes the scope for {@code set_config('app.current_tenants', …, true)},
 * read back by the {@code can_access_tenant(tenant_id)} SQL function.
 */
public sealed interface TxCtx permits TxCtx.Missing, TxCtx.Restricted {

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

  record Missing() implements TxCtx {
    static final Missing INSTANCE = new Missing();

    /** Empty string denies all rows and overrides any configured default. */
    @Override
    public String toGuc() {
      return "";
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
