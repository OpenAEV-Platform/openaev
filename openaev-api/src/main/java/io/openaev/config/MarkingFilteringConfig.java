package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link MarkedTables} the marking dimension filters against, from the live database
 * schema: a table is markable when it has a companion join table named {@code <table>_markings}
 * holding a {@code marking_id} column and exactly one column pointing back at it. The schema is the
 * source of truth for the same reason as {@link TenantFilteringConfig}: the join tables have no
 * entity class of their own on the owning side, and a hand-maintained mapping would silently drift.
 *
 * <p>The derived set is then narrowed to the activation allowlist ({@code
 * openaev.marking.active-tables}), empty by default, so the dimension stays inert until a table is
 * onboarded.
 */
@AllowRawJdbc(reason = "reads information_schema metadata only; no marked rows are accessed")
@Configuration
public class MarkingFilteringConfig {

  private static final String JOIN_TABLE_COLUMNS_QUERY =
      "SELECT table_name, column_name FROM information_schema.columns "
          + "WHERE table_schema = current_schema() AND table_name LIKE '%\\_markings' "
          + "ORDER BY table_name, ordinal_position";

  private static final String MARKED_TABLE_COLUMN_QUERY =
      "SELECT 1 FROM information_schema.columns "
          + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?";

  @Bean
  public MarkedTables markedTables(
      DataSource dataSource,
      @Value("${openaev.marking.active-tables:}") List<String> activeTables) {
    List<String> allowlist = activeTables.stream().filter(name -> !name.isBlank()).toList();
    return deriveFromSchema(dataSource).restrictTo(allowlist);
  }

  @Bean
  public MarkingDimension markingDimension(MarkedTables markedTables) {
    return new MarkingDimension(markedTables);
  }

  static MarkedTables deriveFromSchema(DataSource dataSource) {
    Map<String, List<String>> joinTableColumns = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection()) {
      try (PreparedStatement statement = connection.prepareStatement(JOIN_TABLE_COLUMNS_QUERY);
          ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          joinTableColumns
              .computeIfAbsent(
                  rows.getString("table_name").toLowerCase(Locale.ROOT), k -> new ArrayList<>())
              .add(rows.getString("column_name").toLowerCase(Locale.ROOT));
        }
      }
      Map<String, MarkedTable> marked = new LinkedHashMap<>();
      for (Map.Entry<String, List<String>> entry : joinTableColumns.entrySet()) {
        MarkedTable table = toMarkedTable(connection, entry.getKey(), entry.getValue());
        if (table != null) {
          marked.put(table.table(), table);
        }
      }
      return new MarkedTables(marked);
    } catch (SQLException e) {
      throw new IllegalStateException("cannot derive marked tables from the schema", e);
    }
  }

  /**
   * Maps one {@code <table>_markings} join table to its {@link MarkedTable}, or null when it does
   * not match the convention. Silently ignoring a non-matching table is safe: it only means no
   * table is derived from it, and the allowlist then fails fast on the missing name rather than
   * activating something half-understood.
   */
  private static MarkedTable toMarkedTable(
      Connection connection, String joinTable, List<String> columns) throws SQLException {
    if (columns.size() != 2 || !columns.contains(MarkedTable.MARKING_COLUMN)) {
      return null;
    }
    String fkColumn =
        columns.stream()
            .filter(column -> !MarkedTable.MARKING_COLUMN.equals(column))
            .findFirst()
            .orElse(null);
    if (fkColumn == null) {
      return null;
    }
    String table =
        joinTable.substring(0, joinTable.length() - MarkedTable.JOIN_TABLE_SUFFIX.length());
    // The convention says the FK column is the marked table's own PK column; verify the column
    // really exists there rather than trusting the name, so a mismatch fails at startup instead of
    // producing SQL that cannot be planned.
    try (PreparedStatement statement = connection.prepareStatement(MARKED_TABLE_COLUMN_QUERY)) {
      statement.setString(1, table);
      statement.setString(2, fkColumn);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          return null;
        }
      }
    }
    return new MarkedTable(table, fkColumn, joinTable, fkColumn);
  }
}
