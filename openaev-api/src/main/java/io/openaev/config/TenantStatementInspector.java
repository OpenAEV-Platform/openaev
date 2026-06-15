package io.openaev.config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Rewrites SQL so that every access to a tenant-scoped table is filtered by {@code
 * can_access_tenant}, keeping a transaction limited to the tenants in its {@code
 * app.current_tenants} scope.
 *
 * <p>Security principle: <b>every</b> reference to a tenant-aware table must be filtered. The
 * inspector wraps the tables of the top-level FROM and joins, and rejects (fail-closed) any
 * statement that still references a tenant-aware table anywhere else — a sub-query, a CTE, a join
 * condition — rather than letting it through partially filtered, which would leak rows across
 * tenants. Coverage of those other positions is added over time; until then they are denied.
 */
public class TenantStatementInspector implements StatementInspector {

  private final TenantTables tables;

  public TenantStatementInspector(TenantTables tables) {
    this.tables = tables;
  }

  @Override
  public String inspect(String sql) {
    Statement statement;
    try {
      statement = CCJSqlParserUtil.parse(sql);
    } catch (Exception e) {
      throw new IllegalStateException("refusing to run SQL that cannot be tenant-filtered", e);
    }
    if (statement instanceof PlainSelect select) {
      return rewriteSelect(select);
    }
    throw new IllegalStateException(
        "statement shape not supported by tenant filtering: "
            + statement.getClass().getSimpleName());
  }

  private String rewriteSelect(PlainSelect select) {
    rejectUnfilteredTenantReferences(select);
    FromItem from = select.getFromItem();
    if (from != null) {
      select.setFromItem(filterFromItem(from));
    }
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        join.setRightItem(filterFromItem(join.getRightItem()));
      }
    }
    return select.toString();
  }

  /**
   * Fails closed if a tenant-aware table is referenced anywhere we do not filter: only the
   * top-level FROM and join tables are wrapped, so a tenant table appearing in a sub-query, CTE or
   * join condition would otherwise slip through unfiltered.
   */
  private void rejectUnfilteredTenantReferences(PlainSelect select) {
    Set<String> covered = coveredTableNames(select);
    for (String name : new TablesNamesFinder<Void>().getTables((Statement) select)) {
      if (isTenant(name) && !covered.contains(name.toLowerCase(Locale.ROOT))) {
        throw new IllegalStateException("unfiltered tenant table referenced: " + name);
      }
    }
    // A tenant table sharing a name with a covered one would pass the check above; reject any
    // tenant
    // reference found in a position we do not wrap.
    if (referencesTenant(select.getWhere()) || referencesTenant(select.getHaving())) {
      throw new IllegalStateException("tenant table referenced in an unfiltered sub-query");
    }
    for (SelectItem<?> item : select.getSelectItems()) {
      if (referencesTenant(item.getExpression())) {
        throw new IllegalStateException(
            "tenant table referenced in an unfiltered select sub-query");
      }
    }
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        if (join.getOnExpressions() != null) {
          for (Expression on : join.getOnExpressions()) {
            if (referencesTenant(on)) {
              throw new IllegalStateException(
                  "tenant table referenced in an unfiltered join condition");
            }
          }
        }
      }
    }
  }

  private Set<String> coveredTableNames(PlainSelect select) {
    Set<String> covered = new HashSet<>();
    if (select.getFromItem() instanceof Table table) {
      covered.add(table.getName().toLowerCase(Locale.ROOT));
    }
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        if (join.getRightItem() instanceof Table table) {
          covered.add(table.getName().toLowerCase(Locale.ROOT));
        }
      }
    }
    return covered;
  }

  private boolean referencesTenant(Expression expression) {
    if (expression == null) {
      return false;
    }
    return new TablesNamesFinder<Void>().getTables(expression).stream().anyMatch(this::isTenant);
  }

  private boolean isTenant(String table) {
    return tables.family(table) != TenantTables.Family.NONE;
  }

  /**
   * Returns the given FROM/JOIN item, wrapped in a tenant-filtered sub-query when it is a
   * tenant-aware table. Non-tenant tables are returned unchanged; any other shape is rejected.
   */
  private FromItem filterFromItem(FromItem item) {
    if (!(item instanceof Table table)) {
      throw new IllegalStateException("FROM/JOIN shape not yet covered by tenant filtering");
    }
    TenantTables.Family family = tables.family(table.getName());
    if (family == TenantTables.Family.NONE) {
      return table;
    }
    String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
    String call =
        family == TenantTables.Family.DUAL
            ? "can_access_tenant(" + ref + ".tenant_id, true)"
            : "can_access_tenant(" + ref + ".tenant_id)";
    String wrapped =
        "(SELECT * FROM " + table.getName() + " " + ref + " WHERE " + call + ") AS " + ref;
    try {
      Statement dummy = CCJSqlParserUtil.parse("SELECT * FROM " + wrapped);
      return ((PlainSelect) dummy).getFromItem();
    } catch (Exception e) {
      throw new IllegalStateException("failed to build tenant-filtered subquery", e);
    }
  }
}
