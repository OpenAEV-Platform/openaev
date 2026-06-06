package io.openaev.config;

import io.openaev.context.ExecState;
import io.openaev.context.StateExecutionContext;
import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.update.Update;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@link StatementInspector} that automatically appends {@code WHERE tenant_id = ?} to
 * every SQL statement that references a tenant-aware table as its primary {@code FROM} source.
 *
 * <h3>Table discovery</h3>
 *
 * <p>Rather than maintaining a hardcoded list of table names, the set of tenant-aware tables is
 * built at startup by querying {@link DatabaseMetaData}: every table that has a column named {@code
 * tenant_id} is automatically included. This means adding a {@code tenant_id} column to a new table
 * is sufficient — no code change required.
 *
 * <h3>Why filtering only the main FROM table is sufficient</h3>
 *
 * <p>Every entity belongs to exactly one tenant (non-nullable {@code tenant_id} FK). Once the
 * primary table is filtered, joined tables can only return rows reachable from the filtered set —
 * cross-tenant leakage through JOINs is impossible by construction.
 *
 * <h3>Subquery in FROM</h3>
 *
 * <p>Hibernate occasionally wraps queries in a subquery (e.g. keyset pagination). The inspector
 * recurses one level to apply the filter to the inner SELECT's primary table.
 *
 * <p>This inspector is registered via {@link HibernateConfig}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantStatementInspector implements StatementInspector {

  private final DataSource dataSource;

  /**
   * Populated at startup from {@link DatabaseMetaData} — all tables with a {@code tenant_id}
   * column.
   */
  private final Set<String> tenantAwareTables = new HashSet<>();

  /**
   * Queries the database schema at startup to discover all tables that have a {@code tenant_id}
   * column. Runs once before the first SQL statement is intercepted.
   *
   * <p>Initialization order is guaranteed: Spring creates this bean (and calls
   * {@code @PostConstruct}) before the {@link jakarta.persistence.EntityManagerFactory} — which
   * itself depends on this inspector via {@link HibernateConfig}. The {@link DataSource} is always
   * available at this point.
   */
  @PostConstruct
  void discoverTenantAwareTables() {
    try (Connection conn = dataSource.getConnection()) {
      DatabaseMetaData meta = conn.getMetaData();
      // Query all columns named "tenant_id" across all tables in the current schema
      try (ResultSet columns = meta.getColumns(null, "public", null, "tenant_id")) {
        while (columns.next()) {
          String tableName = columns.getString("TABLE_NAME").toLowerCase();
          tenantAwareTables.add(tableName);
        }
      }
      log.info(
          "TenantStatementInspector: discovered {} tenant-aware tables from schema: {}",
          tenantAwareTables.size(),
          tenantAwareTables);
    } catch (SQLException e) {
      log.error(
          "TenantStatementInspector: failed to discover tenant-aware tables — "
              + "tenant filtering will not be applied. Check DataSource configuration.",
          e);
    }
  }

  @Override
  public String inspect(String sql) {
    ExecState state = StateExecutionContext.get();
    if (state == null) {
      // No tenant context active — bypass filtering (admin / system operations)
      return sql;
    }
    List<String> tenantIds = state.accessibleTenantIds();

    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      String rewritten = sql;
      if (statement instanceof PlainSelect select) {
        rewritten = rewriteSelect(select, tenantIds, sql);
      } else if (statement instanceof SetOperationList setOpList) {
        rewritten = rewriteSetOperationList(setOpList, tenantIds, sql);
      } else if (statement instanceof Update update) {
        rewritten = rewriteUpdate(update, tenantIds, sql);
      } else if (statement instanceof Delete delete) {
        rewritten = rewriteDelete(delete, tenantIds, sql);
      }
      if (!rewritten.equals(sql)) {
        log.warn(
            "TenantStatementInspector [tenants={}]:\n  BEFORE: {}\n  AFTER:  {}",
            tenantIds,
            sql,
            rewritten);
      }
      return rewritten;
    } catch (Exception e) {
      log.warn(
          "TenantStatementInspector: could not parse SQL, returning unchanged. SQL=[{}]", sql, e);
    }
    return sql;
  }

  // ---------------------------------------------------------------------------
  // SELECT
  // ---------------------------------------------------------------------------

  private String rewriteSelect(PlainSelect select, List<String> tenantIds, String originalSql) {
    FromItem fromItem = select.getFromItem();

    if (fromItem instanceof Table table) {
      return applyFilterToTable(select, table, tenantIds);
    }

    if (fromItem instanceof ParenthesedSelect subSel) {
      // Hibernate occasionally wraps queries in a subquery (e.g. keyset pagination).
      // Recurse one level to reach the actual table.
      if (subSel.getSelect() instanceof PlainSelect inner) {
        rewriteSelect(inner, tenantIds, originalSql);
        return select.toString();
      }
    }

    log.debug(
        "TenantStatementInspector: unhandled FROM type [{}], no tenant filter added.",
        fromItem == null ? "null" : fromItem.getClass().getSimpleName());
    return originalSql;
  }

  private String applyFilterToTable(PlainSelect select, Table table, List<String> tenantIds) {
    if (!tenantAwareTables.contains(table.getName().toLowerCase())) {
      return select.toString();
    }
    String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
    select.setWhere(and(select.getWhere(), buildTenantFilter(ref, tenantIds)));
    return select.toString();
  }

  private String rewriteSetOperationList(
      SetOperationList setOpList, List<String> tenantIds, String originalSql) {
    if (setOpList.getSelects() != null) {
      for (Select select : setOpList.getSelects()) {
        if (select instanceof PlainSelect plainSelect) {
          rewriteSelect(plainSelect, tenantIds, originalSql);
        } else if (select instanceof SetOperationList nestedSetOpList) {
          rewriteSetOperationList(nestedSetOpList, tenantIds, originalSql);
        }
      }
    }
    return setOpList.toString();
  }

  // ---------------------------------------------------------------------------
  // UPDATE
  // ---------------------------------------------------------------------------

  private String rewriteUpdate(Update update, List<String> tenantIds, String originalSql) {
    Table table = update.getTable();
    if (table == null || !tenantAwareTables.contains(table.getName().toLowerCase())) {
      return originalSql;
    }
    String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
    update.setWhere(and(update.getWhere(), buildTenantFilter(ref, tenantIds)));
    return update.toString();
  }

  // ---------------------------------------------------------------------------
  // DELETE
  // ---------------------------------------------------------------------------

  private String rewriteDelete(Delete delete, List<String> tenantIds, String originalSql) {
    Table table = delete.getTable();
    if (table == null || !tenantAwareTables.contains(table.getName().toLowerCase())) {
      return originalSql;
    }
    String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
    delete.setWhere(and(delete.getWhere(), buildTenantFilter(ref, tenantIds)));
    return delete.toString();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a tenant filter expression:
   *
   * <ul>
   *   <li>Single tenant → {@code alias.tenant_id = 'xxx'} (EqualsTo, more efficient)
   *   <li>Multiple tenants → {@code alias.tenant_id IN ('xxx', 'yyy')} (grants scenario)
   * </ul>
   */
  private Expression buildTenantFilter(String tableOrAlias, List<String> tenantIds) {
    Column col = new Column(tableOrAlias + ".tenant_id");
    if (tenantIds.size() == 1) {
      return new EqualsTo(col, new StringValue(tenantIds.get(0)));
    }
    ExpressionList<StringValue> values =
        new ExpressionList<>(tenantIds.stream().map(StringValue::new).toList());
    return new InExpression(col, values);
  }

  /** Combines two expressions with AND, returning {@code extra} when {@code base} is null. */
  private Expression and(Expression base, Expression extra) {
    return base == null ? extra : new AndExpression(base, extra);
  }
}
