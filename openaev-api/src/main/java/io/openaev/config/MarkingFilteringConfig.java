package io.openaev.config;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.rest.settings.PreviewFeature;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

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
 *
 * <p>Tables that carry no marking of their own but point to a marked row can then be linked to it
 * ({@code openaev.marking.linked-tables}), so they are hidden whenever that row is. Linking is
 * checked against the schema and against the activated tables at startup.
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

  /**
   * {@code child.fk>parent.key} (the table points to a parent) or {@code table.key<link.fk} (link
   * rows point to the table); four plain identifiers around one arrow, nothing else.
   */
  private static final Pattern LINKED_TABLE_ENTRY =
      Pattern.compile("([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)([<>])([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)");

  private static final String COLUMN_EXISTS_QUERY =
      "SELECT 1 FROM information_schema.columns "
          + "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?";

  @Bean
  public MarkedTables markedTables(
      DataSource dataSource,
      @Value("${openaev.enabled-dev-features:}") String enabledDevFeatures,
      @Value("${openaev.marking.active-tables:}") List<String> activeTables,
      @Value("${openaev.marking.linked-tables:}") List<String> linkedTables) {
    if (!isMarkingFeatureEnabled(enabledDevFeatures)) {
      return MarkedTables.EMPTY;
    }
    List<String> allowlist = activeTables.stream().filter(name -> !name.isBlank()).toList();
    List<MarkedTable> links = parseLinkedTables(linkedTables);
    requireLinkColumnsExist(dataSource, links);
    return deriveFromSchema(dataSource).restrictTo(allowlist).withLinked(links);
  }

  /**
   * Parses {@code openaev.marking.linked-tables}: tables marked through another table instead of a
   * {@code marking_ids} column of their own. Each entry reads either {@code
   * child.foreign_key>parent.parent_key} (the table points to its parent, e.g. {@code
   * agents.agent_asset>assets.asset_id}) or {@code table.key<link.foreign_key} (link rows point to
   * the table, e.g. {@code findings.finding_id<findings_assets.finding_id}).
   *
   * <p>A malformed entry fails the startup rather than being skipped: a skipped entry would leave a
   * table the operator believes protected, unfiltered.
   */
  static List<MarkedTable> parseLinkedTables(List<String> entries) {
    return entries.stream()
        .map(String::strip)
        .filter(entry -> !entry.isEmpty())
        .map(MarkingFilteringConfig::parseLinkedTable)
        .toList();
  }

  private static MarkedTable parseLinkedTable(String entry) {
    Matcher matcher = LINKED_TABLE_ENTRY.matcher(entry);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "openaev.marking.linked-tables entry must read child.fk>parent.key or"
              + " table.key<link.fk, got: "
              + entry);
    }
    String table = matcher.group(1);
    String column = matcher.group(2);
    String otherTable = matcher.group(4);
    String otherColumn = matcher.group(5);
    return ">".equals(matcher.group(3))
        ? MarkedTable.linkedTo(table, column, otherTable, otherColumn)
        : MarkedTable.throughLinkRows(table, column, otherTable, otherColumn);
  }

  /**
   * Checks that every column a link names exists, so a typo surfaces at startup instead of as a
   * query error (or, worse, a table that never matches and is silently unprotected).
   */
  private void requireLinkColumnsExist(DataSource dataSource, List<MarkedTable> links) {
    if (links.isEmpty()) {
      return;
    }
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(COLUMN_EXISTS_QUERY)) {
      for (MarkedTable link : links) {
        for (MarkedTable.ColumnRef ref : link.linkedColumns()) {
          requireColumn(statement, ref.table(), ref.column());
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException("cannot check the marking links against the schema", e);
    }
  }

  private static void requireColumn(PreparedStatement statement, String table, String column)
      throws SQLException {
    statement.setString(1, table);
    statement.setString(2, column);
    try (ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) {
        throw new IllegalArgumentException(
            "openaev.marking.linked-tables names a column that does not exist: "
                + table
                + "."
                + column);
      }
    }
  }

  /**
   * Unconditional counterpart to {@link #markedTables}: every schema table with a {@code
   * marking_ids} column, regardless of the feature flag or the activation allowlist. See {@link
   * AllTablesWithMarkingIds} for why delete-time scrubbing must use this set instead.
   */
  @Bean
  public AllTablesWithMarkingIds allTablesWithMarkingIds(DataSource dataSource) {
    return new AllTablesWithMarkingIds(deriveFromSchema(dataSource));
  }

  /**
   * Same semantics as {@code PreviewFeatureService.isFeatureEnabled}: {@code
   * openaev.enabled-dev-features} is a comma-separated, case-insensitive list, and {@link
   * PreviewFeature#FEATURE_FLAG_ALL} ({@code "*"}) enables every preview feature including {@link
   * PreviewFeature#MARKING}.
   *
   * <p>Instead of injecting {@code PreviewFeatureService.isFeatureEnabled(MARKING)} do the call
   * here to avoid a circular dependency in Hibernate life cycle. Same issue than the usage of JDBC
   * query.
   */
  private static boolean isMarkingFeatureEnabled(String enabledDevFeatures) {
    if (!StringUtils.hasText(enabledDevFeatures)) {
      return false;
    }
    return Arrays.stream(enabledDevFeatures.split(","))
        .map(String::strip)
        .anyMatch(
            token ->
                token.equalsIgnoreCase(PreviewFeature.FEATURE_FLAG_ALL.getValue())
                    || token.equalsIgnoreCase(PreviewFeature.MARKING.getValue()));
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
