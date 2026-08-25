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
 * Rewrites SQL so that every access to a scoped table is filtered by the predicates of the active
 * {@link ScopeDimension}s, keeping a transaction limited to the scope carried by its session
 * variables. With the tenant dimension alone this restricts statements to {@code
 * app.current_tenants}; a second dimension (marking clearance) ANDs its own predicate onto the same
 * tables.
 *
 * <p>Security principle: <b>every</b> reference to a scoped table must be filtered. SELECT tables
 * (FROM, joins, sub-queries, CTEs) are wrapped in a filtered sub-query; the target of an UPDATE or
 * DELETE gets the filter added to its WHERE (a written table cannot be wrapped). Completeness comes
 * from visiting every select; a statement, FROM or join shape that is not understood is rejected
 * (fail-closed) rather than passed through unfiltered, which would leak rows across scopes.
 *
 * <p>Refusal messages still read "tenant filtering": they are kept verbatim from the tenant-only
 * inspector this class generalizes, so the extraction is provably behaviour-preserving. They become
 * scope-generic when a second dimension is activated.
 */
public class ScopeStatementInspector implements StatementInspector {

  private final List<ScopeDimension> dimensions;
  private final Pattern activeTablePattern;

  public ScopeStatementInspector(List<ScopeDimension> dimensions) {
    this.dimensions = List.copyOf(dimensions);
    this.activeTablePattern = buildActiveTablePattern(this.dimensions);
  }

  /**
   * Matches any active table name on identifier boundaries (so {@code asset} does not match inside
   * {@code asset_groups}). Null when no table is active, which keeps the inspector inert. Hibernate
   * always emits the physical table name, so a statement touching an active table always contains
   * that name, which is what lets the gate below skip everything else without missing a row.
   */
  private static Pattern buildActiveTablePattern(List<ScopeDimension> dimensions) {
    Set<String> names = new HashSet<>();
    for (ScopeDimension dimension : dimensions) {
      names.addAll(dimension.activeTables());
    }
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
    update.setWhere(combineScopeFilter(update.getTable(), update.getWhere(), false));
    return update.toString();
  }

  private String rewriteDelete(Delete delete) {
    // Multi-target delete or an explicit join is a shape we do not cover yet.
    if (notEmpty(delete.getTables()) || notEmpty(delete.getJoins())) {
      throw new TenantFilteringException("DELETE ... <multi-target or join> not yet covered");
    }
    filterContainedSelects(delete);
    // The USING list is typed List<Table>, so it cannot be wrapped in a sub-query like a FROM; each
    // scoped read source is filtered through the WHERE instead.
    Expression where = combineScopeFilter(delete.getTable(), delete.getWhere(), false);
    if (delete.getUsingList() != null) {
      for (Table using : delete.getUsingList()) {
        where = combineScopeFilter(using, where, true);
      }
    }
    delete.setWhere(where);
    return delete.toString();
  }

  private String rewriteInsert(Insert insert) {
    // An ON CONFLICT DO UPDATE could touch an existing, possibly out-of-scope, row on conflict. It
    // is guarded the same way as an UPDATE: the scope predicates are added to the DO UPDATE WHERE,
    // so a conflicting row outside the scope is left untouched. DO NOTHING needs no guard.
    var conflict = insert.getConflictAction();
    if (conflict != null
        && conflict.getConflictActionType() == ConflictActionType.DO_UPDATE
        && insert.getTable() != null
        && isCovered(insert.getTable())) {
      conflict.setWhereExpression(
          combineScopeFilter(insert.getTable(), conflict.getWhereExpression(), false));
    }
    // An INSERT ... SELECT into a scoped table must write only in-scope values; the written scope
    // column is validated against the scope. VALUES inserts cannot be distinguished from
    // ORM-generated ones at the SQL level, so their scope assignment stays an application concern.
    Select source = insert.getSelect();
    if (insert.getTable() != null && source != null && !(source instanceof Values)) {
      for (ScopeDimension dimension : dimensions) {
        if (dimension.covers(insert.getTable().getName())
            && dimension.writeAttributionColumn() != null) {
          validateInsertSelectScope(insert, source, dimension);
        }
      }
    }
    // The SELECT source (and any sub-query) is read-filtered like any select.
    filterContainedSelects(insert);
    return insert.toString();
  }

  /**
   * Adds the dimension's attribution predicate on the written scope column to the source SELECT of
   * an {@code INSERT ... SELECT} into a covered table, so only rows whose scope column is in scope
   * are inserted (a write, so no permissive flag). Anything that cannot be mapped to a single
   * projected expression (no column list, no scope column, a {@code SELECT *}, or a non-plain
   * source) is refused, which also refuses an omitted scope column (a platform-row write).
   */
  private void validateInsertSelectScope(Insert insert, Select select, ScopeDimension dimension) {
    String scopeColumn = dimension.writeAttributionColumn();
    ExpressionList<Column> columns = insert.getColumns();
    if (!(select instanceof PlainSelect source) || columns == null) {
      throw new TenantFilteringException(
          "INSERT ... SELECT into a "
              + dimension.name()
              + " table needs an explicit column list and a plain SELECT");
    }
    int scopeIdx = -1;
    for (int i = 0; i < columns.size(); i++) {
      String name = columns.get(i).getColumnName();
      if (name != null && name.replace("\"", "").equalsIgnoreCase(scopeColumn)) {
        scopeIdx = i;
        break;
      }
    }
    if (scopeIdx < 0) {
      throw new TenantFilteringException(
          "INSERT ... SELECT into a "
              + dimension.name()
              + " table must set "
              + scopeColumn
              + " in scope");
    }
    List<SelectItem<?>> items = source.getSelectItems();
    if (items == null || scopeIdx >= items.size()) {
      throw new TenantFilteringException(
          "INSERT ... SELECT: " + scopeColumn + " cannot be mapped to a projected expression");
    }
    for (SelectItem<?> item : items) {
      if (item.getExpression() instanceof AllColumns) {
        throw new TenantFilteringException(
            "INSERT ... SELECT * into a "
                + dimension.name()
                + " table cannot validate the written "
                + scopeColumn);
      }
    }
    Expression scopeExpr = items.get(scopeIdx).getExpression();
    // The expression is referenced a second time in the WHERE. A bind parameter cannot be: the
    // inspector must not change the placeholder count, or positional binding breaks. Refuse it.
    if (scopeExpr.toString().contains("?")) {
      throw new TenantFilteringException(
          "INSERT ... SELECT: a bind-parameter "
              + scopeColumn
              + " cannot be validated by rewriting");
    }
    source.setWhere(
        combineCall(source.getWhere(), dimension.writeAttributionPredicate(scopeExpr.toString())));
  }

  /**
   * ANDs a scope predicate into an existing WHERE, or returns it alone. Explicit parentheses keep
   * precedence when the existing WHERE is an OR; a WHERE we cannot re-parse is refused
   * (fail-closed) rather than left unfiltered.
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

  /** Wraps the FROM and join scoped tables of a single select level. */
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
   * Wraps a scoped table in a filtered sub-query. Unscoped tables and nested sub-queries (filtered
   * on their own, as their own select) are returned unchanged; any other shape is rejected.
   */
  private FromItem filterFromItem(FromItem item) {
    // Sub-selects and lateral sub-selects are never real tables; they do not need scope
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
    String ref = reference(table);
    String predicates = scopePredicates(table, ref, true);
    if (predicates == null) {
      return table;
    }
    String wrapped =
        "(SELECT * FROM "
            + table.getName()
            + " "
            + ref
            + " WHERE "
            + predicates
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
   * Adds the scope predicates of a table to a WHERE clause (the table itself cannot be wrapped). A
   * read on a dual-scope table also lets platform rows through; a write never does, so the
   * read/write distinction is delegated to each dimension.
   */
  private Expression combineScopeFilter(Table table, Expression existing, boolean read) {
    if (table == null) {
      return existing;
    }
    String predicates = scopePredicates(table, reference(table), read);
    return predicates == null ? existing : combineCall(existing, predicates);
  }

  /**
   * ANDs one predicate per dimension covering the table, or null when no dimension does. With a
   * single active dimension the result is that dimension's predicate verbatim, so activating a
   * second dimension is the only thing that changes the emitted SQL.
   */
  private String scopePredicates(Table table, String alias, boolean read) {
    String name = table.getName();
    List<String> predicates = new ArrayList<>();
    for (ScopeDimension dimension : dimensions) {
      if (dimension.covers(name)) {
        predicates.add(
            read ? dimension.readPredicate(name, alias) : dimension.writePredicate(name, alias));
      }
    }
    return predicates.isEmpty() ? null : String.join(" AND ", predicates);
  }

  private boolean isCovered(Table table) {
    return dimensions.stream().anyMatch(dimension -> dimension.covers(table.getName()));
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
