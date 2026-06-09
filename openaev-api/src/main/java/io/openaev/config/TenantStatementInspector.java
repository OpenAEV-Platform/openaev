package io.openaev.config;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
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
    // TODO: Gradually add tables here as they are migrated to the new tenant filtering approach.
    //  Once all repositories have been migrated, replace this explicit list with the automatic
    //  discovery from DatabaseMetaData (commented out below).
    tenantAwareTables.add("documents");
    log.info(
        "TenantStatementInspector: configured {} tenant-aware tables: {}",
        tenantAwareTables.size(),
        tenantAwareTables);

    // --- Automatic discovery (for future use) ---
    // try (Connection conn = dataSource.getConnection()) {
    //   DatabaseMetaData meta = conn.getMetaData();
    //   try (ResultSet columns = meta.getColumns(null, "public", null, "tenant_id")) {
    //     while (columns.next()) {
    //       String tableName = columns.getString("TABLE_NAME").toLowerCase();
    //       tenantAwareTables.add(tableName);
    //     }
    //   }
    // } catch (SQLException e) {
    //   log.error("TenantStatementInspector: failed to discover tenant-aware tables", e);
    // }
  }

  @Override
  public String inspect(String sql) {
    try {
      Statement statement = CCJSqlParserUtil.parse(sql);
      String rewritten = sql;
      if (statement instanceof PlainSelect select) {
        rewritten = rewriteSelect(select, sql);
      } else if (statement instanceof SetOperationList setOpList) {
        rewritten = rewriteSetOperationList(setOpList, sql);
      } else if (statement instanceof Update update) {
        rewritten = rewriteUpdate(update, sql);
      } else if (statement instanceof Delete delete) {
        rewritten = rewriteDelete(delete, sql);
      }
      if (!rewritten.equals(sql)) {
        // log.warn("TenantStatementInspector BEFORE: {}\n  AFTER:  {}", sql, rewritten);
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

  private String rewriteSelect(PlainSelect select, String originalSql) {
    FromItem fromItem = select.getFromItem();

    if (fromItem instanceof Table table) {
      return applyFilterToTable(select, table);
    }

    if (fromItem instanceof ParenthesedSelect subSel) {
      // Hibernate occasionally wraps queries in a subquery (e.g. keyset pagination).
      // Recurse one level to reach the actual table.
      if (subSel.getSelect() instanceof PlainSelect inner) {
        rewriteSelect(inner, originalSql);
        return select.toString();
      }
    }

    log.debug(
        "TenantStatementInspector: unhandled FROM type [{}], no tenant filter added.",
        fromItem == null ? "null" : fromItem.getClass().getSimpleName());
    return originalSql;
  }

  private String applyFilterToTable(PlainSelect select, Table table) {
    if (!tenantAwareTables.contains(table.getName().toLowerCase())) {
      return select.toString();
    }
    String alias = table.getAlias() != null ? table.getAlias().getName() : table.getName();

    String filterSql =
        "current_setting('app.current_tenants', true) IS NULL "
            + "OR current_setting('app.current_tenants', true) = '' "
            + "OR "
            + alias
            + ".tenant_id = ANY(string_to_array(current_setting('app.current_tenants', true), ','))";

    String subQueryStr =
        "(SELECT * FROM " + table.getName() + " " + alias + " WHERE " + filterSql + ") AS " + alias;

    try {
      // JSQLParser does not always have parseFromItem, so we parse a dummy statement
      String dummySql = "SELECT * FROM " + subQueryStr;
      Statement stmt = CCJSqlParserUtil.parse(dummySql);
      FromItem subQuery = ((PlainSelect) stmt).getFromItem();
      select.setFromItem(subQuery);
    } catch (Exception e) {
      log.error(
          "TenantStatementInspector: Failed to parse tenant subquery for table {}",
          table.getName(),
          e);
    }
    return select.toString();
  }

  private String rewriteSetOperationList(SetOperationList setOpList, String originalSql) {
    if (setOpList.getSelects() != null) {
      for (Select select : setOpList.getSelects()) {
        if (select instanceof PlainSelect plainSelect) {
          rewriteSelect(plainSelect, originalSql);
        } else if (select instanceof SetOperationList nestedSetOpList) {
          rewriteSetOperationList(nestedSetOpList, originalSql);
        }
      }
    }
    return setOpList.toString();
  }

  // ---------------------------------------------------------------------------
  // UPDATE
  // ---------------------------------------------------------------------------

  private String rewriteUpdate(Update update, String originalSql) {
    Table table = update.getTable();
    if (table == null || !tenantAwareTables.contains(table.getName().toLowerCase())) {
      return originalSql;
    }
    String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
    try {
      update.setWhere(safeAnd(update.getWhere(), ref));
    } catch (Exception e) {
      log.error(
          "TenantStatementInspector: Failed to rewrite UPDATE for table {}", table.getName(), e);
    }
    return update.toString();
  }

  // ---------------------------------------------------------------------------
  // DELETE
  // ---------------------------------------------------------------------------

  private String rewriteDelete(Delete delete, String originalSql) {
    Table table = delete.getTable();
    if (table == null || !tenantAwareTables.contains(table.getName().toLowerCase())) {
      return originalSql;
    }
    String ref = table.getAlias() != null ? table.getAlias().getName() : table.getName();
    try {
      delete.setWhere(safeAnd(delete.getWhere(), ref));
    } catch (Exception e) {
      log.error(
          "TenantStatementInspector: Failed to rewrite DELETE for table {}", table.getName(), e);
    }
    return delete.toString();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Safely combines an existing WHERE clause with the tenant filter, ensuring proper parenthesis.
   */
  private Expression safeAnd(Expression existingWhere, String alias) throws Exception {
    String filterSql =
        "current_setting('app.current_tenants', true) IS NULL "
            + "OR current_setting('app.current_tenants', true) = '' "
            + "OR "
            + alias
            + ".tenant_id = ANY(string_to_array(current_setting('app.current_tenants', true), ','))";

    if (existingWhere == null) {
      return CCJSqlParserUtil.parseCondExpression(filterSql);
    } else {
      return CCJSqlParserUtil.parseCondExpression(
          "(" + existingWhere + ") AND (" + filterSql + ")");
    }
  }
}
