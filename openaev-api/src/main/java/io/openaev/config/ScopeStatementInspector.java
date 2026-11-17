package io.openaev.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
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
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
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
    guardAssignedValues(update.getTable().getName(), update.getUpdateSets());
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
      guardAssignedValues(insert.getTable().getName(), conflict.getUpdateSets());
    }
    Select source = insert.getSelect();
    if (insert.getTable() != null && source instanceof Values values) {
      // The sub-queries of the VALUES tuples are read-filtered like any select. A guarding
      // dimension (today: a dual-scope table's written tenant) wraps the written value in place;
      // see guardValuesInsert. Every other table's VALUES insert is returned unchanged: scope
      // attribution for it is an application concern.
      filterContainedSelects(insert);
      guardValuesInsert(insert, values);
      return insert.toString();
    }
    if (insert.getTable() != null && source == null) {
      refuseIfValuesInsertIsGuarded(insert.getTable().getName(), "has no VALUES source");
    } else if (insert.getTable() != null) {
      // An INSERT ... SELECT into a scoped table must write only in-scope values; the written scope
      // column is validated against the scope.
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
   * Refuses an {@code INSERT} into a table whose written scope column a dimension validates by
   * rewriting, when the statement offers nothing to validate: the column would be written as NULL,
   * which for a dual-scope table is a platform row created with no check.
   */
  private void refuseIfValuesInsertIsGuarded(String tableName, String why) {
    for (ScopeDimension dimension : dimensions) {
      if (dimension.covers(tableName)
          && dimension.writeAttributionColumn() != null
          && dimension.guardsWrittenValue(tableName)) {
        throw new TenantFilteringException(
            "INSERT into a "
                + dimension.name()
                + " table must set "
                + dimension.writeAttributionColumn()
                + " in a column list and a VALUES list: this one "
                + why);
      }
    }
  }

  /**
   * Passes the written scope column of {@code INSERT INTO t (cols) VALUES (...)} through the
   * dimension's guard, in each row, when a dimension guards this table (today: {@link
   * TenantDimension#guardedWrittenValue} for a dual-scope table). The value stays where it is, so
   * the statement keeps its shape, its placeholder count and their order, and PostgreSQL still
   * types a {@code NULL} literal or a bind parameter of another column by the target column. A
   * table no dimension guards (every table outside the platform-write rule, every strict table) is
   * left unchanged.
   *
   * <p>A dimension that guards this table ({@link ScopeDimension#guardsWrittenValue}) refuses a
   * statement with no column list or without its written scope column: the omitted column is
   * written as NULL, and there is no value to guard.
   */
  private void guardValuesInsert(Insert insert, Values values) {
    String tableName = insert.getTable().getName();
    ExpressionList<Column> columns = insert.getColumns();
    if (columns == null) {
      refuseIfValuesInsertIsGuarded(tableName, "has no column list");
      return;
    }
    for (ScopeDimension dimension : dimensions) {
      // Only a table whose written value is guarded is checked: a strict table's VALUES insert is
      // left to the database, whatever its shape.
      String column =
          dimension.covers(tableName) && dimension.guardsWrittenValue(tableName)
              ? dimension.writeAttributionColumn()
              : null;
      if (column == null) {
        continue;
      }
      int idx = columnIndex(columns, column);
      if (idx < 0) {
        refuseIfValuesInsertIsGuarded(tableName, "does not name " + column);
        continue;
      }
      for (ExpressionList<Expression> row : rowsOf(values)) {
        if (idx >= row.size()) {
          throw new TenantFilteringException(
              "INSERT into a "
                  + dimension.name()
                  + " table has a VALUES row shorter than its column list");
        }
        Expression guarded = guardedValue(dimension, tableName, row.get(idx));
        if (guarded != null) {
          row.set(idx, guarded);
        }
      }
    }
  }

  /** The rows of a VALUES list: one expression list per tuple, one row for a single tuple. */
  @SuppressWarnings("unchecked")
  private static List<ExpressionList<Expression>> rowsOf(Values values) {
    ExpressionList<?> expressions = values.getExpressions();
    boolean tuples =
        !expressions.isEmpty() && expressions.stream().allMatch(e -> e instanceof ExpressionList);
    if (!tuples) {
      return List.of((ExpressionList<Expression>) expressions);
    }
    List<ExpressionList<Expression>> rows = new ArrayList<>();
    for (Object row : expressions) {
      rows.add((ExpressionList<Expression>) row);
    }
    return rows;
  }

  /**
   * Passes the value an {@code UPDATE ... SET} or an {@code ON CONFLICT DO UPDATE SET} assigns to
   * the written scope column through the dimension's guard, so a write cannot re-assign a row to
   * another scope (a tenant row to the platform, or to a tenant outside the transaction's scope).
   */
  private void guardAssignedValues(String tableName, List<UpdateSet> updateSets) {
    if (updateSets == null) {
      return;
    }
    for (ScopeDimension dimension : dimensions) {
      // A dimension that guards nothing written to this table leaves the assignment as it is, the
      // shape of a tuple included.
      String column =
          dimension.covers(tableName) && dimension.guardsWrittenValue(tableName)
              ? dimension.writeAttributionColumn()
              : null;
      if (column == null) {
        continue;
      }
      for (UpdateSet set : updateSets) {
        int idx = columnIndex(set.getColumns(), column);
        if (idx < 0) {
          continue;
        }
        // A value per column, in order: a sub-select or a list of another size cannot be guarded
        // per column, so it is refused rather than written unchecked.
        ExpressionList<?> values = set.getValues();
        if (values == null || values.size() != set.getColumns().size()) {
          throw new TenantFilteringException(
              "UPDATE ... SET of a "
                  + dimension.name()
                  + " table assigns "
                  + column
                  + " from a value that does not line up with its columns");
        }
        @SuppressWarnings("unchecked")
        ExpressionList<Expression> assigned = (ExpressionList<Expression>) values;
        Expression guarded = guardedValue(dimension, tableName, assigned.get(idx));
        if (guarded != null) {
          assigned.set(idx, guarded);
        }
      }
    }
  }

  /** The dimension's guard around {@code value}, or {@code null} when it guards nothing here. */
  private static Expression guardedValue(
      ScopeDimension dimension, String tableName, Expression value) {
    String guarded = dimension.guardedWrittenValue(tableName, value.toString());
    if (guarded == null) {
      return null;
    }
    try {
      return CCJSqlParserUtil.parseExpression(guarded);
    } catch (Exception e) {
      throw new TenantFilteringException("could not guard the written value " + value, e);
    }
  }

  /** The index of {@code name} in {@code columns}, ignoring surrounding quotes, or -1. */
  private static int columnIndex(ExpressionList<Column> columns, String name) {
    for (int i = 0; i < columns.size(); i++) {
      String candidate = columns.get(i).getColumnName();
      if (candidate != null && candidate.replace("\"", "").equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Adds the dimension's attribution predicate on the written scope column to the source SELECT of
   * an {@code INSERT ... SELECT} into a covered table, so only rows whose scope column is in scope
   * are inserted (a write, so no permissive flag). Anything that cannot be mapped to a single
   * projected expression (no column list, no scope column, a {@code SELECT *}, or a non-plain
   * source) is refused, which also refuses an omitted scope column (a platform-row write).
   *
   * <p>A dimension that guards the table's written value ({@link
   * ScopeDimension#guardsWrittenValue}) wraps the projected expression itself, so the value that is
   * checked is the value that is written even when the expression gives another result each time it
   * is evaluated, and a bind parameter keeps its place.
   */
  @SuppressWarnings("unchecked")
  private void validateInsertSelectScope(Insert insert, Select select, ScopeDimension dimension) {
    String scopeColumn = dimension.writeAttributionColumn();
    ExpressionList<Column> columns = insert.getColumns();
    if (!(select instanceof PlainSelect source) || columns == null) {
      throw new TenantFilteringException(
          "INSERT ... SELECT into a "
              + dimension.name()
              + " table needs an explicit column list and a plain SELECT");
    }
    int scopeIdx = columnIndex(columns, scopeColumn);
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
    SelectItem<?> scopeItem = items.get(scopeIdx);
    Expression scopeExpr = scopeItem.getExpression();
    Expression guarded = guardedValue(dimension, insert.getTable().getName(), scopeExpr);
    if (guarded != null) {
      ((SelectItem<Expression>) scopeItem).setExpression(guarded);
      return;
    }
    // The expression is referenced a second time in the WHERE. A bind parameter cannot be: the
    // inspector must not change the placeholder count, or positional binding breaks. Refuse it.
    if (scopeExpr.toString().contains("?")) {
      throw new TenantFilteringException(
          "INSERT ... SELECT: a bind-parameter "
              + scopeColumn
              + " cannot be validated by rewriting");
    }
    source.setWhere(
        combineCall(
            source.getWhere(),
            dimension.writeAttributionPredicate(
                insert.getTable().getName(), scopeExpr.toString())));
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
    // Descendants before ancestors: narrowing a primary table re-parses its select's WHERE
    // (combineCall), rebuilding any sub-query in that WHERE from its string form. Filtering the
    // inner select first bakes its own predicate into that string, so the rebuilt copy keeps it.
    // The collector lists ancestors first (pre-order), so we walk it in reverse.
    List<PlainSelect> collected = collector.collected;
    for (int i = collected.size() - 1; i >= 0; i--) {
      filterTables(collected.get(i));
    }
  }

  /**
   * Filters the FROM and join scoped tables of a single select level. Joined tables are wrapped in
   * a filtered sub-query. The primary FROM item, when it is a plain scoped table, is instead
   * filtered through the select's WHERE: wrapping it in a derived table would strip the primary
   * key's functional dependency, so a {@code GROUP BY id} projecting other columns becomes invalid
   * SQL in PostgreSQL. Moving the predicate to the WHERE is equivalent only while the primary table
   * is never NULL-extended, so a RIGHT or FULL join anywhere in the join list forces a fallback to
   * wrapping (a WHERE predicate on the NULL-extended side would drop those rows and silently turn
   * the outer join into an inner one). Every other primary shape is wrapped exactly as before.
   */
  private void filterTables(PlainSelect select) {
    FromItem from = select.getFromItem();
    if (from instanceof Table table && isCovered(table) && !hasRightOrFullJoin(select.getJoins())) {
      select.setWhere(combineScopeFilter(table, select.getWhere(), true));
    } else if (from != null) {
      select.setFromItem(filterFromItem(from));
    }
    if (select.getJoins() != null) {
      for (Join join : select.getJoins()) {
        join.setRightItem(filterFromItem(join.getRightItem()));
      }
    }
  }

  /**
   * Whether the join list NULL-extends the accumulated left side, which is what makes moving the
   * primary table's predicate into the WHERE unsafe. A RIGHT join NULL-extends the left side, a
   * FULL join both sides; either anywhere in the list rules out the narrowing for this select
   * level.
   */
  private static boolean hasRightOrFullJoin(List<Join> joins) {
    if (joins == null) {
      return false;
    }
    for (Join join : joins) {
      if (join.isRight() || join.isFull()) {
        return true;
      }
    }
    return false;
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
    private final Set<PlainSelect> seen = Collections.newSetFromMap(new IdentityHashMap<>());

    @Override
    public <S> Void visit(PlainSelect plainSelect, S context) {
      // The finder reaches the body of a WITH item twice: filter it once.
      if (seen.add(plainSelect)) {
        collected.add(plainSelect);
      }
      return super.visit(plainSelect, context);
    }

    @Override
    public <S> Void visit(WithItem<?> withItem, S context) {
      // Only SELECT bodies are rewritten, so a data-modifying CTE would pass unfiltered.
      if (!(withItem.getParenthesedStatement() instanceof ParenthesedSelect)) {
        throw new TenantFilteringException("data-modifying CTE not supported by scope filtering");
      }
      return super.visit(withItem, context);
    }
  }
}
