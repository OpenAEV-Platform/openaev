package io.openaev.config;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Rewrites SQL so that every access to a tenant-scoped table is filtered by {@code
 * can_access_tenant}, keeping a transaction limited to the tenants in its {@code
 * app.current_tenants} scope.
 *
 * <p>Security principle: <b>every</b> reference to a tenant-aware table must be filtered. The
 * inspector wraps the tables of the top-level FROM and joins, and rejects (fail-closed) any
 * statement that references a tenant-aware table anywhere else — a sub-query, a CTE, a join
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
    rejectUnwrappedTenantReferences(select);
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
   * Fails closed if a tenant-aware table is referenced anywhere other than the top-level FROM and
   * joins (the only positions we wrap) — for example in a sub-query, a CTE or a join condition. The
   * check compares table nodes by identity, so it does not depend on names or on traversal depth: a
   * nested reference sharing a name with a filtered table is still caught.
   */
  private void rejectUnwrappedTenantReferences(PlainSelect select) {
    Set<Table> topLevel = Collections.newSetFromMap(new IdentityHashMap<>());
    if (select.getFromItem() instanceof Table table) {
      topLevel.add(table);
    }
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        if (join.getRightItem() instanceof Table table) {
          topLevel.add(table);
        }
      }
    }
    UnwrappedTenantDetector detector = new UnwrappedTenantDetector(topLevel);
    detector.getTables((Statement) select);
    if (detector.found) {
      throw new IllegalStateException("tenant table referenced outside the filtered FROM/joins");
    }
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

  /**
   * Walks the whole statement and flags any tenant-aware table that is not one of the top-level
   * FROM/join tables we wrap — i.e. a tenant table reached through a sub-query, CTE or join
   * condition. Tables are matched by identity, so names and nesting depth are irrelevant.
   */
  private final class UnwrappedTenantDetector extends TablesNamesFinder<Void> {
    private final Set<Table> topLevel;
    private boolean found = false;

    private UnwrappedTenantDetector(Set<Table> topLevel) {
      this.topLevel = topLevel;
    }

    @Override
    public <S> Void visit(Table table, S context) {
      if (!topLevel.contains(table) && isTenant(table.getName())) {
        found = true;
      }
      return super.visit(table, context);
    }
  }
}
