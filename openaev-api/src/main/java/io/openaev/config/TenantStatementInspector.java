package io.openaev.config;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Rewrites SQL so that every access to a tenant-scoped table is filtered by {@code
 * can_access_tenant}, keeping a transaction limited to the tenants in its {@code
 * app.current_tenants} scope.
 *
 * <p>Security principle: <b>every</b> reference to a tenant-aware table must be filtered — not only
 * the main FROM table, but joins, subqueries and CTEs as well. Any statement shape that cannot be
 * fully covered is <b>rejected (fail-closed)</b> rather than passed through partially filtered,
 * which would leak rows across tenants.
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
    // Shapes not yet covered are denied rather than passed through unfiltered.
    throw new IllegalStateException(
        "statement shape not supported by tenant filtering: "
            + statement.getClass().getSimpleName());
  }

  private String rewriteSelect(PlainSelect select) {
    if (select.getJoins() != null && !select.getJoins().isEmpty()) {
      throw new IllegalStateException("joins are not yet covered by tenant filtering");
    }
    FromItem from = select.getFromItem();
    if (from == null) {
      return select.toString();
    }
    if (!(from instanceof Table table)) {
      throw new IllegalStateException("FROM shape not yet covered by tenant filtering");
    }
    filterTable(select, table);
    return select.toString();
  }

  private void filterTable(PlainSelect select, Table table) {
    TenantTables.Family family = tables.family(table.getName());
    if (family == TenantTables.Family.NONE) {
      return;
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
      select.setFromItem(((PlainSelect) dummy).getFromItem());
    } catch (Exception e) {
      throw new IllegalStateException("failed to build tenant-filtered subquery", e);
    }
  }
}
