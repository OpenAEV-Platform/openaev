package io.openaev.config;

import java.util.List;

/**
 * The tenant-only configuration of {@link ScopeStatementInspector}: rewrites SQL so that every
 * access to a tenant-scoped table is filtered by {@code can_access_tenant}, keeping a transaction
 * limited to the tenants in its {@code app.current_tenants} scope.
 *
 * <p>All the rewriting lives in the generic inspector; this type only binds it to the single {@link
 * TenantDimension}. It stays the injected bean type so tenant isolation keeps a name of its own in
 * the wiring and in the tests that pin it as the inspector Hibernate runs.
 */
public class TenantStatementInspector extends ScopeStatementInspector {

  public TenantStatementInspector(TenantTables tables) {
    super(List.of(new TenantDimension(tables)));
  }
}
