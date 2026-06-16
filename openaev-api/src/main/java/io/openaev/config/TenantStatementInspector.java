package io.openaev.config;

import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Rewrites SQL so that every access to a tenant-scoped table is filtered by {@code
 * can_access_tenant}, keeping a transaction limited to the tenants in its {@code
 * app.current_tenants} scope.
 *
 * <p>Security principle: <b>every</b> reference to a tenant-aware table must be filtered. The
 * inspector visits every select in the statement — the top one, joins, sub-queries, CTEs — and
 * wraps each one's FROM and join tables in a filtered sub-query. Completeness comes from visiting
 * every select; a statement, FROM or join shape that is not understood is rejected (fail-closed)
 * rather than passed through unfiltered, which would leak rows across tenants.
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
    if (statement instanceof Select select) {
      PlainSelectCollector collector = new PlainSelectCollector();
      collector.getTables((Statement) select);
      for (PlainSelect plainSelect : collector.collected) {
        filterTables(plainSelect);
      }
      return select.toString();
    }
    throw new IllegalStateException(
        "statement shape not supported by tenant filtering: "
            + statement.getClass().getSimpleName());
  }

  /** Wraps the FROM and join tenant tables of a single select level. */
  private void filterTables(PlainSelect select) {
    if (select.getFromItem() != null) {
      select.setFromItem(filterFromItem(select.getFromItem()));
    }
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        join.setRightItem(filterFromItem(join.getRightItem()));
      }
    }
  }

  /**
   * Wraps a tenant-aware table in a filtered sub-query. Non-tenant tables and nested sub-queries
   * (filtered on their own, as their own select) are returned unchanged; any other shape is
   * rejected.
   */
  private FromItem filterFromItem(FromItem item) {
    if (item instanceof ParenthesedSelect) {
      return item;
    }
    if (!(item instanceof Table table)) {
      throw new IllegalStateException(
          "FROM/JOIN shape not yet covered by tenant filtering: "
              + (item == null ? "null" : item.getClass().getSimpleName()));
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
   * Collects every {@link PlainSelect} in the statement — top-level and nested — so each one's FROM
   * and joins can be filtered. Relies on the finder visiting every select node.
   */
  private static final class PlainSelectCollector extends TablesNamesFinder<Void> {
    private final List<PlainSelect> collected = new ArrayList<>();

    @Override
    public <S> Void visit(PlainSelect plainSelect, S context) {
      collected.add(plainSelect);
      return super.visit(plainSelect, context);
    }
  }
}
