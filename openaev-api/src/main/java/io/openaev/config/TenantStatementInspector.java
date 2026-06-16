package io.openaev.config;

import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.ConflictActionType;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Rewrites SQL so that every access to a tenant-scoped table is filtered by {@code
 * can_access_tenant}, keeping a transaction limited to the tenants in its {@code
 * app.current_tenants} scope.
 *
 * <p>Security principle: <b>every</b> reference to a tenant-aware table must be filtered. SELECT
 * tables (FROM, joins, sub-queries, CTEs) are wrapped in a filtered sub-query; the target of an
 * UPDATE or DELETE gets the filter added to its WHERE (a written table cannot be wrapped).
 * Completeness comes from visiting every select; a statement, FROM or join shape that is not
 * understood is rejected (fail-closed) rather than passed through unfiltered, which would leak rows
 * across tenants.
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
      throw new TenantFilteringException("refusing to run SQL that cannot be tenant-filtered", e);
    }
    if (statement instanceof Select select) {
      filterContainedSelects(select);
      return select.toString();
    }
    if (statement instanceof Update update) {
      return rewriteUpdate(update);
    }
    if (statement instanceof Delete delete) {
      return rewriteDelete(delete);
    }
    if (statement instanceof Insert insert) {
      return rewriteInsert(insert);
    }
    throw new TenantFilteringException(
        "statement shape not supported by tenant filtering: "
            + statement.getClass().getSimpleName());
  }

  private String rewriteUpdate(Update update) {
    if (update.getFromItem() != null
        || notEmpty(update.getJoins())
        || notEmpty(update.getStartJoins())) {
      throw new TenantFilteringException(
          "UPDATE ... FROM/JOIN not yet covered by tenant filtering");
    }
    filterContainedSelects(update);
    update.setWhere(withTenantPredicate(update.getTable(), update.getWhere()));
    return update.toString();
  }

  private String rewriteDelete(Delete delete) {
    if (notEmpty(delete.getUsingList())
        || notEmpty(delete.getJoins())
        || notEmpty(delete.getTables())) {
      throw new TenantFilteringException(
          "DELETE ... USING/JOIN not yet covered by tenant filtering");
    }
    filterContainedSelects(delete);
    delete.setWhere(withTenantPredicate(delete.getTable(), delete.getWhere()));
    return delete.toString();
  }

  private String rewriteInsert(Insert insert) {
    // An ON CONFLICT DO UPDATE could touch an existing, possibly cross-tenant, row on conflict. It
    // is guarded the same way as an UPDATE: can_access_tenant is added to the DO UPDATE WHERE, so a
    // conflicting row outside the scope is left untouched. DO NOTHING needs no guard.
    var conflict = insert.getConflictAction();
    if (conflict != null
        && conflict.getConflictActionType() == ConflictActionType.DO_UPDATE
        && insert.getTable() != null
        && tables.family(insert.getTable().getName()) != TenantTables.Family.NONE) {
      conflict.setWhereExpression(
          withTenantPredicate(insert.getTable(), conflict.getWhereExpression()));
    }
    // Only the SELECT source (and any sub-query) is filtered. A VALUES insert has no read to
    // filter; validating the target tenant on write is the write-side policy (B3).
    filterContainedSelects(insert);
    return insert.toString();
  }

  /** Wraps the FROM and join tables of every select contained in the statement. */
  private void filterContainedSelects(Statement statement) {
    PlainSelectCollector collector = new PlainSelectCollector();
    collector.getTables(statement);
    for (PlainSelect plainSelect : collector.collected) {
      filterTables(plainSelect);
    }
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
    if (item instanceof ParenthesedFromItem group) {
      // A parenthesized join group: filter its inner FROM and joins like any other level.
      group.setFromItem(filterFromItem(group.getFromItem()));
      if (group.getJoins() != null) {
        for (Join join : group.getJoins()) {
          join.setRightItem(filterFromItem(join.getRightItem()));
        }
      }
      return group;
    }
    if (!(item instanceof Table table)) {
      throw new TenantFilteringException(
          "FROM/JOIN shape not yet covered by tenant filtering: "
              + (item == null ? "null" : item.getClass().getSimpleName()));
    }
    TenantTables.Family family = tables.family(table.getName());
    if (family == TenantTables.Family.NONE) {
      return table;
    }
    String ref = reference(table);
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
   * Adds {@code can_access_tenant} on the written table to a WHERE clause (the table itself cannot
   * be wrapped). A write never reaches platform rows from a tenant scope — {@code allow_platform}
   * is not passed — pending the platform-write policy.
   */
  private Expression withTenantPredicate(Table target, Expression existing) {
    if (target == null || tables.family(target.getName()) == TenantTables.Family.NONE) {
      return existing;
    }
    String call = "can_access_tenant(" + reference(target) + ".tenant_id)";
    // Explicit parentheses keep precedence when the existing WHERE is an OR. A WHERE we cannot
    // re-parse is refused (fail-closed) rather than left unfiltered.
    String combined = existing == null ? call : "(" + existing + ") AND (" + call + ")";
    try {
      return CCJSqlParserUtil.parseCondExpression(combined);
    } catch (Exception e) {
      throw new TenantFilteringException("could not add the tenant filter to the WHERE clause", e);
    }
  }

  private static String reference(Table table) {
    return table.getAlias() != null ? table.getAlias().getName() : table.getName();
  }

  private static boolean notEmpty(List<?> list) {
    return list != null && !list.isEmpty();
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
