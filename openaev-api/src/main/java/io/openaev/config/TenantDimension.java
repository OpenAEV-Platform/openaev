package io.openaev.config;

import java.util.HashSet;
import java.util.Set;

/**
 * The tenant scope dimension: restricts every covered table to the tenants in the transaction's
 * {@code app.current_tenants} scope, through the {@code can_access_tenant} SQL function.
 *
 * <p>A read on a dual-scope table also lets platform rows through ({@code allow_platform}), with or
 * without a scope: a platform row belongs to no tenant. That holds for every dual-scope table, not
 * for {@code parameters} alone: the SQL function has one answer for all of them, so a table armed
 * later (a platform {@code groups} or {@code roles} row) is also returned to a read with no scope,
 * which hides the missing scope that would otherwise read nothing. Each such table pins that read
 * with a test of its own when it is armed ({@code DualScopeUnscopedPlatformReadGuardTest}). A write
 * to a dual-scope table's platform row ({@code tenant_id IS NULL}) is allowed only inside a
 * transaction the server has marked platform-write ({@code can_write_tenant}'s {@code
 * allow_platform_write} branch, backed by the {@code app.platform_write} setting); a write to its
 * tenant row needs its tenant in the scope. A write of a platform row the policy refuses raises
 * ({@code require_tenant_write}), whether it is an {@code INSERT}, an {@code UPDATE} or a {@code
 * DELETE}. A strict table's write predicate is unchanged by any of this.
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

  /**
   * For a dual-scope table, a row the statement matches is written when {@code can_write_tenant}
   * allows it. A platform row it refuses ends in {@code require_tenant_write}'s error, never in a
   * statement that matches zero rows and reports success; a tenant row outside the scope stays
   * outside the statement, as for a strict table. The raising call is declared costly, so the
   * planner evaluates it after the statement's own conditions.
   */
  @Override
  public String writePredicate(String table, String alias) {
    if (tables.family(table) != TenantTables.Family.DUAL) {
      return "can_access_tenant(" + alias + ".tenant_id)";
    }
    String tenant = alias + ".tenant_id";
    return "(can_write_tenant("
        + tenant
        + ", true) OR ("
        + tenant
        + " IS NULL AND require_tenant_write("
        + tenant
        + ", true)))";
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
   * Used for a plain {@code INSERT ... VALUES}, an {@code UPDATE ... SET} and an {@code ON CONFLICT
   * DO UPDATE SET}. Only dual-scope tables are guarded here: the written tenant is passed through
   * {@code tenant_write_value}, which returns it when the write is allowed and raises otherwise (a
   * platform row needs the marker, a tenant row needs its tenant in the scope). A strict table's
   * write is not validated (see the interface javadoc for why).
   */
  @Override
  public String guardedWrittenValue(String table, String valueExpression) {
    if (tables.family(table) != TenantTables.Family.DUAL) {
      return null;
    }
    return "tenant_write_value(" + valueExpression + ", true)";
  }

  @Override
  public boolean guardsWrittenValue(String table) {
    return tables.family(table) == TenantTables.Family.DUAL;
  }
}
