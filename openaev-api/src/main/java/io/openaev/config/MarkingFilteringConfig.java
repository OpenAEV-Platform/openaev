package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * schema: a table is markable when it holds a {@code marking_ids} array column. The schema is the
 * source of truth for the same reason as {@link TenantFilteringConfig} — a hand-maintained mapping
 * would silently drift from the migrations.
 *
 * <p>The array type is part of the test, not decoration. It is what distinguishes a marking column
 * from a scalar column that merely shares the name, so a mistyped migration fails at startup rather
 * than producing a predicate Postgres cannot plan.
 *
 * <p>The derived set is then narrowed to the activation allowlist ({@code
 * openaev.marking.active-tables}), empty by default, so the dimension stays inert until a table is
 * onboarded.
 */
@AllowRawJdbc(reason = "reads information_schema metadata only; no marked rows are accessed")
@Configuration
public class MarkingFilteringConfig {

  /**
   * {@code data_type = 'ARRAY'} is how information_schema reports any array column; {@code
   * udt_name} then carries the element type prefixed with an underscore, hence {@code _text} for
   * {@code text[]}. Both are checked so a {@code marking_ids integer[]} is rejected too.
   */
  private static final String MARKED_TABLE_QUERY =
      "SELECT c.table_name FROM information_schema.columns c "
          + "JOIN information_schema.tables t "
          + "  ON t.table_schema = c.table_schema AND t.table_name = c.table_name "
          + "WHERE c.table_schema = current_schema() "
          + "  AND t.table_type = 'BASE TABLE' "
          + "  AND c.column_name = ? "
          + "  AND c.data_type = 'ARRAY' "
          + "  AND c.udt_name IN ('_text', '_varchar') "
          + "ORDER BY c.table_name";

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
    Map<String, MarkedTable> marked = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(MARKED_TABLE_QUERY)) {
      statement.setString(1, MarkedTable.MARKING_COLUMN);
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          String table = rows.getString("table_name").toLowerCase(Locale.ROOT);
          marked.put(table, new MarkedTable(table));
        }
      }
      return new MarkedTables(marked);
    } catch (SQLException e) {
      throw new IllegalStateException("cannot derive marked tables from the schema", e);
    }
  }
}
