package io.openaev.config;

import java.util.HashSet;
import java.util.Set;

/**
 * The tenant scope dimension: restricts every covered table to the tenants in the transaction's
 * {@code app.current_tenants} scope, through the {@code can_access_tenant} SQL function.
 *
 * <p>A read on a dual-scope table also lets platform rows through ({@code allow_platform}). A write
 * to a dual-scope table's platform row ({@code tenant_id IS NULL}) is allowed only inside a
 * transaction the server has marked platform-write ({@code can_write_tenant}'s {@code
 * allow_platform_write} branch, backed by the {@code app.platform_write} setting); a write to its
 * tenant row is checked exactly like a strict table's, unaffected by the platform-write policy. A
 * strict table's write predicate is unchanged by any of this.
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
    return tables.family(table) == TenantTables.Family.DUAL
        ? "can_write_tenant(" + alias + ".tenant_id, true)"
        : "can_access_tenant(" + alias + ".tenant_id)";
  }

  @Override
  public String writeAttributionColumn() {
    return "tenant_id";
  }

  /** Used for an {@code INSERT ... SELECT}: both the tenant and the platform case are checked. */
  @Override
  public String writeAttributionPredicate(String table, String valueExpression) {
    return tables.family(table) == TenantTables.Family.DUAL
        ? "can_write_tenant(" + valueExpression + ", true)"
        : "can_access_tenant(" + valueExpression + ")";
  }

  /**
   * Used for a plain {@code INSERT ... VALUES}. Only dual-scope tables are guarded here, and only
   * for the platform-row case: a tenant row stays exactly as unvalidated as a strict table's {@code
   * VALUES} insert is today (see the interface javadoc for why), so this never rejects a write that
   * would have succeeded before a table became dual-scope-active.
   */
  @Override
  public String writeValuesAttributionPredicate(String table, String valueExpression) {
    if (tables.family(table) != TenantTables.Family.DUAL) {
      return null;
    }
    return valueExpression + " IS NOT NULL OR is_platform_write()";
  }
}
