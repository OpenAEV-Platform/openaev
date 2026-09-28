package io.openaev.config;

import java.util.HashSet;
import java.util.Set;

/**
 * The tenant scope dimension: restricts every covered table to the tenants in the transaction's
 * {@code app.current_tenants} scope, through the {@code can_access_tenant} SQL function.
 *
 * <p>A read on a dual-scope table also lets platform rows through ({@code allow_platform}); a write
 * never does, pending the platform-write policy.
 */
public final class TenantDimension implements ScopeDimension {

  private final TenantTables tables;

  public TenantDimension(TenantTables tables) {
    this.tables = tables;
  }

  @Override
  public String name() {
    return "tenant";
  }

  @Override
  public Set<String> activeTables() {
    Set<String> names = new HashSet<>(tables.strict());
    names.addAll(tables.dualScope());
    return names;
  }

  @Override
  public boolean covers(String table) {
    return tables.family(table) != TenantTables.Family.NONE;
  }

  @Override
  public String readPredicate(String table, String alias) {
    return tables.family(table) == TenantTables.Family.DUAL
        ? "can_access_tenant(" + alias + ".tenant_id, true)"
        : "can_access_tenant(" + alias + ".tenant_id)";
  }

  @Override
  public String writePredicate(String table, String alias) {
    return "can_access_tenant(" + alias + ".tenant_id)";
  }

  @Override
  public String writeAttributionColumn() {
    return "tenant_id";
  }

  @Override
  public String writeAttributionPredicate(String valueExpression) {
    return "can_access_tenant(" + valueExpression + ")";
  }
}
