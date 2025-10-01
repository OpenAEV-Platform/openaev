package io.openaev.utilstest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.openaev.config.EngineConfig;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSnapshotManager {

  private final JdbcTemplate jdbcTemplate;
  private final ElasticsearchClient esClient;
  private final EngineConfig config;

  private static final Map<String, List<Map<String, Object>>> startupData = new HashMap<>();
  private static final List<String> TABLE_WITHOUT_RESTORATION = List.of("indexing_status");
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

  /** Restore database to the snapshot */
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

      cleanElasticsearchIndices();

      // Deactivate FK for now
      jdbcTemplate.execute("SET session_replication_role = 'replica';");

      // Empty tables
      List<String> reverseOrder = new ArrayList<>(tablesInOrder);
      Collections.reverse(reverseOrder);
      for (String table : reverseOrder) {
        jdbcTemplate.execute("DELETE FROM " + table);
      }

      // Restore tables in the correct order
      for (String table : tablesInOrder) {
        if (!TABLE_WITHOUT_RESTORATION.contains(table)) {
          restoreTableData(table);
        }
      }

      // Activate FK back
      jdbcTemplate.execute("SET session_replication_role = 'origin';");

      log.info("Database restored to startup state via JDBC");

    } catch (Exception e) {
      throw new RuntimeException("Error restoring startup state", e);
    }
  }

  /** Delete ES indices */
  private void cleanElasticsearchIndices() {
    if (esClient == null) {
      return;
    }

    try {
      esClient
          .indices()
          .delete(
              d ->
                  d.index(config.getIndexPrefix() + "_*")
                      .ignoreUnavailable(true)
                      .allowNoIndices(true));
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
   * @param table the table to restore
   */
  private void restoreTableData(String table) {
    List<Map<String, Object>> data = startupData.get(table);
    if (data == null || data.isEmpty()) return;

    for (Map<String, Object> row : data) {
      String columns = String.join(", ", row.keySet());
      String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));

      String sql = "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")";
      jdbcTemplate.update(sql, row.values().toArray());
    }
  }
}
