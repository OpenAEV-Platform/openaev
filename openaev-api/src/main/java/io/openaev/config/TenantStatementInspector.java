package io.openaev.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.ConflictActionType;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.LateralSubSelect;
import net.sf.jsqlparser.statement.select.ParenthesedFromItem;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.TableFunction;
import net.sf.jsqlparser.statement.select.Values;
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
  private final Pattern activeTablePattern;

  public TenantStatementInspector(TenantTables tables) {
    this.tables = tables;
    this.activeTablePattern = buildActiveTablePattern(tables);
  }

  /**
   * Matches any active table name on identifier boundaries (so {@code asset} does not match inside
   * {@code asset_groups}). Null when no table is active, which keeps the inspector inert. Hibernate
   * always emits the physical table name, so a statement touching an active table always contains
   * that name, which is what lets the gate below skip everything else without missing a row.
   */
  private static Pattern buildActiveTablePattern(TenantTables tables) {
    Set<String> names = new HashSet<>();
    names.addAll(tables.strict());
    names.addAll(tables.dualScope());
    if (names.isEmpty()) {
      return null;
    }
    String alternation = names.stream().map(Pattern::quote).collect(Collectors.joining("|"));
    return Pattern.compile(
        "(?<![A-Za-z0-9_])(" + alternation + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
  }

  @Override
  public String inspect(String sql) {
    if (sql == null || activeTablePattern == null) {
      return sql;
    }
    // Blank string literals before the gate: an active table name that appears only inside a
    // literal must not pull an unrelated (possibly unparseable) statement into rewriting. The
    // parser, not this gate, is the source of truth for real table references.
    String gateSql = sql.replaceAll("'(?:[^']|'')*'", "''");
    if (!activeTablePattern.matcher(gateSql).find()) {
      return sql;
    }
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
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
    } catch (TenantFilteringException e) {
      throw e;
    } catch (Exception e) {
      // Any parse or rewrite failure is refused, never passed through unfiltered (fail-closed).
      throw new TenantFilteringException("refusing SQL the tenant filter could not process", e);
    }
  }

  private String rewriteUpdate(Update update) {
    // A target-side join (UPDATE t JOIN ... SET ...) is a shape we do not cover yet.
    if (notEmpty(update.getStartJoins())) {
      throw new TenantFilteringException("UPDATE ... <target join> not yet covered");
    }
    filterContainedSelects(update);
    // The FROM read sources are wrapped in a filtered sub-query, exactly like a SELECT's FROM.
    if (update.getFromItem() != null) {
      update.setFromItem(filterFromItem(update.getFromItem()));
    }
    if (update.getJoins() != null) {
      for (Join join : update.getJoins()) {
        join.setRightItem(filterFromItem(join.getRightItem()));
      }
    }
    update.setWhere(withTenantPredicate(update.getTable(), update.getWhere()));
    return update.toString();
  }

  private String rewriteDelete(Delete delete) {
    // Multi-target delete or an explicit join is a shape we do not cover yet.
    if (notEmpty(delete.getTables()) || notEmpty(delete.getJoins())) {
      throw new TenantFilteringException("DELETE ... <multi-target or join> not yet covered");
    }
    filterContainedSelects(delete);
    // The USING list is typed List<Table>, so it cannot be wrapped in a sub-query like a FROM; each
    // tenant read source is filtered through the WHERE instead.
    Expression where = withTenantPredicate(delete.getTable(), delete.getWhere());
    if (delete.getUsingList() != null) {
      for (Table using : delete.getUsingList()) {
        where = combineTenantFilter(using, where, true);
      }
    }
    delete.setWhere(where);
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
    // An INSERT ... SELECT into a tenant table must write only in-scope tenant_id values; the
    // inserted tenant_id is validated against the scope. VALUES inserts cannot be distinguished
    // from
    // ORM-generated ones at the SQL level, so their tenant assignment stays an application concern.
    Select source = insert.getSelect();
    if (insert.getTable() != null
        && tables.family(insert.getTable().getName()) != TenantTables.Family.NONE
        && source != null
        && !(source instanceof Values)) {
      validateInsertSelectTenant(insert, source);
    }
    // The SELECT source (and any sub-query) is read-filtered like any select.
    filterContainedSelects(insert);
    return insert.toString();
  }

  /**
   * Adds {@code can_access_tenant} on the written {@code tenant_id} to the source SELECT of an
   * {@code INSERT ... SELECT} into a tenant table, so only rows whose tenant_id is in scope are
   * inserted (a write, so no {@code allow_platform}). Anything that cannot be mapped to a single
   * projected expression (no column list, no {@code tenant_id} column, a {@code SELECT *}, or a
   * non-plain source) is refused, which also refuses an omitted tenant_id (a platform-row write).
   */
  private void validateInsertSelectTenant(Insert insert, Select select) {
    ExpressionList<Column> columns = insert.getColumns();
    if (!(select instanceof PlainSelect source) || columns == null) {
      throw new TenantFilteringException(
          "INSERT ... SELECT into a tenant table needs an explicit column list and a plain SELECT");
    }
    int tenantIdx = -1;
    for (int i = 0; i < columns.size(); i++) {
      String name = columns.get(i).getColumnName();
      if (name != null && name.replace("\"", "").equalsIgnoreCase("tenant_id")) {
        tenantIdx = i;
        break;
      }
    }
    if (tenantIdx < 0) {
      throw new TenantFilteringException(
          "INSERT ... SELECT into a tenant table must set tenant_id in scope");
    }
    List<SelectItem<?>> items = source.getSelectItems();
    if (items == null || tenantIdx >= items.size()) {
      throw new TenantFilteringException(
          "INSERT ... SELECT: tenant_id cannot be mapped to a projected expression");
    }
    for (SelectItem<?> item : items) {
      if (item.getExpression() instanceof AllColumns) {
        throw new TenantFilteringException(
            "INSERT ... SELECT * into a tenant table cannot validate the written tenant_id");
      }
    }
    Expression tenantExpr = items.get(tenantIdx).getExpression();
    // The expression is referenced a second time in the WHERE. A bind parameter cannot be: the
    // inspector must not change the placeholder count, or positional binding breaks. Refuse it.
    if (tenantExpr.toString().contains("?")) {
      throw new TenantFilteringException(
          "INSERT ... SELECT: a bind-parameter tenant_id cannot be validated by rewriting");
    }
    source.setWhere(combineCall(source.getWhere(), "can_access_tenant(" + tenantExpr + ")"));
  }

  /**
   * ANDs a {@code can_access_tenant(...)} call into an existing WHERE, or returns it alone.
   * Explicit parentheses keep precedence when the existing WHERE is an OR; a WHERE we cannot
   * re-parse is refused (fail-closed) rather than left unfiltered.
   */
  private Expression combineCall(Expression existing, String call) {
    String combined = existing == null ? call : "(" + existing + ") AND (" + call + ")";
    try {
      return CCJSqlParserUtil.parseCondExpression(combined);
    } catch (Exception e) {
      throw new TenantFilteringException("could not add the tenant filter to the WHERE clause", e);
    }
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
    // Sub-selects and lateral sub-selects are never real tables; they do not need tenant
    // filtering. A LATERAL table function (e.g. "LEFT JOIN LATERAL jsonb_array_elements(...)")
    // unnests a column of the row already being joined, never a whole table, so it is safe too.
    // A non-lateral table function (e.g. "CROSS JOIN generate_series(1, 10)") is NOT unnesting an
    // existing row and is not a shape this rewriter has reviewed; it stays rejected.
    if (item instanceof ParenthesedSelect || item instanceof LateralSubSelect) {
      return item;
    }
    if (item instanceof TableFunction tableFunction
        && "LATERAL".equalsIgnoreCase(tableFunction.getPrefix())) {
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
    if (tables.family(table.getName()) == TenantTables.Family.NONE) {
      return table;
    }
    String ref = reference(table);
    String wrapped =
        "(SELECT * FROM "
            + table.getName()
            + " "
            + ref
            + " WHERE "
            + tenantCall(table, true)
            + ")"
            + " AS "
            + ref;
    Statement dummy;
    try {
      dummy = CCJSqlParserUtil.parse("SELECT * FROM " + wrapped);
    } catch (Exception e) {
      throw new IllegalStateException("failed to build tenant-filtered subquery", e);
    }
    // wrapped is always a plain SELECT, but guard the cast: a non-plain result fails with a clear
    // message instead of a raw ClassCastException (fail-closed by the inspector either way).
    if (dummy instanceof PlainSelect plain) {
      return plain.getFromItem();
    }
    throw new IllegalStateException(
        "expected a plain select when building the tenant-filtered subquery, got "
            + dummy.getClass().getSimpleName());
  }

  /**
   * Adds {@code can_access_tenant} on the written table to a WHERE clause (the table itself cannot
   * be wrapped). A write never reaches platform rows from a tenant scope — {@code allow_platform}
   * is not passed — pending the platform-write policy.
   */
  private Expression withTenantPredicate(Table target, Expression existing) {
    return combineTenantFilter(target, existing, false);
  }

  /**
   * Adds {@code can_access_tenant} on a table to a WHERE clause (the table itself cannot be
   * wrapped). A read on a dual-scope table also lets platform rows through; a write never does, so
   * {@code allow_platform} is passed only for read sources.
   */
  private Expression combineTenantFilter(Table table, Expression existing, boolean read) {
    if (table == null || tables.family(table.getName()) == TenantTables.Family.NONE) {
      return existing;
    }
    return combineCall(existing, tenantCall(table, read));
  }

  private String tenantCall(Table table, boolean read) {
    String ref = reference(table);
    return read && tables.family(table.getName()) == TenantTables.Family.DUAL
        ? "can_access_tenant(" + ref + ".tenant_id, true)"
        : "can_access_tenant(" + ref + ".tenant_id)";
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
