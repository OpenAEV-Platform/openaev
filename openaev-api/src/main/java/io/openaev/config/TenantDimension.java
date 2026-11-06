package io.openaev.config;

import java.util.HashSet;
import java.util.Set;

/**
 * The tenant scope dimension: restricts every covered table to the tenants in the transaction's
 * {@code app.current_tenants} scope, through the {@code can_access_tenant} SQL function.
 *
 * <p>A read on a dual-scope table also lets platform rows through ({@code allow_platform}), with or
 * without a scope: a platform row belongs to no tenant. A write to a dual-scope table's platform
 * row ({@code tenant_id IS NULL}) is allowed only inside a transaction the server has marked
 * platform-write ({@code can_write_tenant}'s {@code allow_platform_write} branch, backed by the
 * {@code app.platform_write} setting); a write to its tenant row needs its tenant in the scope. An
 * {@code INSERT} the policy refuses raises ({@code require_tenant_write}). A strict table's write
 * predicate is unchanged by any of this.
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

  /**
   * Used for an {@code INSERT ... SELECT}. A dual-scope table's written tenant is checked through
   * {@code require_tenant_write}, which raises when the write is refused: a row the policy refuses
   * is an error the caller sees, never a row silently left out.
   */
  @Override
  public String writeAttributionPredicate(String table, String valueExpression) {
    return tables.family(table) == TenantTables.Family.DUAL
        ? "require_tenant_write(" + valueExpression + ", true)"
        : "can_access_tenant(" + valueExpression + ")";
  }

  /**
   * Used for a plain {@code INSERT ... VALUES}. Only dual-scope tables are guarded here, for the
   * platform row (it needs the platform-write marker) and for a tenant row (its tenant must be in
   * the scope); a strict table's {@code VALUES} insert is not validated (see the interface javadoc
   * for why). The check raises when the write is refused.
   */
  @Override
  public String writeValuesAttributionPredicate(String table, String valueExpression) {
    if (tables.family(table) != TenantTables.Family.DUAL) {
      return null;
    }
    return "require_tenant_write(" + valueExpression + ", true)";
  }

  @Override
  public boolean guardsValuesInsert(String table) {
    return tables.family(table) == TenantTables.Family.DUAL;
  }
}
