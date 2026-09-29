package io.openaev.utilstest;

import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Component;

/**
 * Snapshots every table once, at the first application start of the fork, and puts the database
 * back to that state after each test class.
 *
 * <p>The restore is one transaction on one connection. Scheduled threads of cached Spring contexts
 * keep running while it works (a health probe that finds its setting missing re-creates it), so
 * emptying the tables and refilling them must not be observable in between: another connection sees
 * the state before the restore or the state after it, never an empty table. A restore that fails
 * rolls back, names the table and the row it could not insert, and leaves the foreign key guard on,
 * because {@code SET LOCAL} ends with the transaction.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSnapshotManager {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectProvider<io.openaev.engine.facade.EngineService> engineFacade;
  private final EngineContext engineContext;

  private static final Map<String, List<Map<String, Object>>> startupData = new HashMap<>();
  private static final List<String> TABLE_WITHOUT_RESTORATION = List.of("indexing_status");
  private static final int ROW_DESCRIPTION_MAX_LENGTH = 1000;
  private static List<String> tablesInOrder;

  private static boolean snapshotCreated = false;

  /** Create a snapshot of the database at startup */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    createSnapshot();
  }

  /** Create a snapshot of the database */
  public void createSnapshot() {
    synchronized (DatabaseSnapshotManager.class) {
      if (snapshotCreated) return;

      try {

        List<String> tables =
            jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
                String.class);

        for (String table : tables) {
          List<Map<String, Object>> tableData = jdbcTemplate.queryForList("SELECT * FROM " + table);
          startupData.put(table, new ArrayList<>(tableData));
        }

        snapshotCreated = true;
        log.info("Startup snapshot created with JDBC ({} tables)", startupData.size());

      } catch (Exception e) {
        log.error("Failed to create startup snapshot: {}", e.getMessage(), e);
      }
    }
  }

  /**
   * Restore database to the snapshot. Atomic: either the database holds exactly the snapshot
   * afterwards, or nothing changed and the exception names the table and the row that could not be
   * restored. This closes the empty-table window, not concurrent writes: a writer that commits a
   * brand-new row between the DELETE and the COMMIT is not undone, since the restore never deletes
   * a second time.
   */
  public void restoreToSnapshotState() {
    if (!snapshotCreated) {
      log.error("Snapshot not created yet, cannot restore!");
      return;
    }

    try {
      // Get tables order
      if (tablesInOrder == null) {
        tablesInOrder = getTablesInDependencyOrder();
      }

      cleanElasticsearchIndices(engineContext.getModels());

      jdbcTemplate.execute(
          (Connection connection) -> {
            restoreInOneTransaction(connection);
            return null;
          });

      log.info("Database restored to startup state via JDBC");

    } catch (Exception e) {
      throw new IllegalStateException("Error restoring startup state: " + e.getMessage(), e);
    }
  }

  private void restoreInOneTransaction(Connection connection) throws SQLException {
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    JdbcTemplate transaction = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    boolean committed = false;
    try {
      // Deactivate FK for this transaction only: SET LOCAL is undone by commit and rollback alike
      transaction.execute("SET LOCAL session_replication_role = 'replica'");

      // Empty tables
      List<String> reverseOrder = new ArrayList<>(tablesInOrder);
      Collections.reverse(reverseOrder);
      for (String table : reverseOrder) {
        transaction.execute("DELETE FROM " + table);
      }

      // Restore tables in the correct order
      for (String table : tablesInOrder) {
        if (!TABLE_WITHOUT_RESTORATION.contains(table)) {
          restoreTableData(transaction, table);
        }
      }

      connection.commit();
      committed = true;
    } catch (Exception e) {
      if (!committed) {
        try {
          connection.rollback();
        } catch (SQLException rollbackFailure) {
          e.addSuppressed(rollbackFailure);
        }
      }
      throw e;
    } finally {
      connection.setAutoCommit(autoCommit);
    }
  }

  /** Delete ES indices */
  private void cleanElasticsearchIndices(List<EsModel<EsBase>> models) {
    EngineService service = engineFacade.getIfAvailable();
    if (service == null) {
      return;
    }

    try {
      for (EsModel<EsBase> model : models) {
        service.cleanUpIndex(model.getName(), false);
      }
      log.info("Deleted all openaev_* Elasticsearch indices");
    } catch (Exception e) {
      log.warn("Could not clean Elasticsearch: {}", e.getMessage());
    }
  }

  /**
   * Get the list of tables in dependency order
   *
   * @return the list of tables in dependency order
   */
  private List<String> getTablesInDependencyOrder() {
    // Get all the dependencies
    List<Map<String, Object>> dependencies =
        jdbcTemplate.queryForList(
            """
                    SELECT
                        tc.table_name as dependent_table,
                        ccu.table_name as referenced_table
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                        ON tc.constraint_name = kcu.constraint_name
                    JOIN information_schema.constraint_column_usage ccu
                        ON ccu.constraint_name = tc.constraint_name
                    WHERE tc.constraint_type = 'FOREIGN KEY'
                        AND tc.table_schema = 'public'
                        AND tc.table_name != ccu.table_name
                    """);

    // Get all tables
    Set<String> allTables =
        new HashSet<>(
            jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class));

    // Build dependency graph
    Map<String, Set<String>> deps = new HashMap<>();
    for (String table : allTables) {
      deps.put(table, new HashSet<>());
    }

    for (Map<String, Object> dep : dependencies) {
      String dependent = (String) dep.get("dependent_table");
      String referenced = (String) dep.get("referenced_table");
      deps.get(dependent).add(referenced);
    }

    // Topological sort
    List<String> result = new ArrayList<>();
    Set<String> processed = new HashSet<>();

    while (processed.size() < allTables.size()) {
      for (String table : allTables) {
        if (!processed.contains(table)) {
          // Check if the table has already been added
          if (processed.containsAll(deps.get(table))) {
            result.add(table);
            processed.add(table);
          }
        }
      }
    }

    return result;
  }

  /**
   * Restore the data of a specific table
   *
   * @param transaction the template bound to the restore transaction
   * @param table the table to restore
   */
  private void restoreTableData(JdbcTemplate transaction, String table) {
    List<Map<String, Object>> data = startupData.get(table);
    if (data == null || data.isEmpty()) return;

    Map<String, String> userDefinedColumns = getUserDefinedColumns(transaction, table);

    for (Map<String, Object> row : data) {
      List<String> columnsList = new ArrayList<>(row.keySet());
      String columns = String.join(", ", columnsList);
      String placeholders =
          columnsList.stream()
              .map(
                  column -> {
                    String udt = userDefinedColumns.get(column);
                    return udt != null ? "?::" + udt : "?";
                  })
              .collect(Collectors.joining(", "));

      String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
      Object[] values = columnsList.stream().map(row::get).toArray();
      try {
        transaction.update(sql, values);
      } catch (DataAccessException e) {
        throw new IllegalStateException(
            "Cannot restore snapshot row of table " + table + ": " + describe(row), e);
      }
    }
  }

  private static String describe(Map<String, Object> row) {
    String description = row.toString();
    return description.length() <= ROW_DESCRIPTION_MAX_LENGTH
        ? description
        : description.substring(0, ROW_DESCRIPTION_MAX_LENGTH) + "...";
  }

  private Map<String, String> getUserDefinedColumns(JdbcTemplate transaction, String table) {
    List<Map<String, Object>> rows =
        transaction.queryForList(
            """
            SELECT column_name, udt_name
              FROM information_schema.columns
             WHERE table_schema = 'public'
               AND table_name = ?
               AND data_type = 'USER-DEFINED'
            """,
            table);

    Map<String, String> result = new HashMap<>();
    for (Map<String, Object> row : rows) {
      result.put((String) row.get("column_name"), (String) row.get("udt_name"));
    }
    return result;
  }
}
