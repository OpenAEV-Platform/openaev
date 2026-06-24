package io.openaev.debug;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort, regex-based mapping of JDBC placeholders ({@code ?}) to their column, so the logger
 * can mask values bound to a sensitive column. Handles {@code INSERT ... VALUES (?, ...)}
 * (positional) and {@code col = ?} (UPDATE/WHERE). Unknown placeholders yield {@code null}; never
 * throws.
 */
public final class SqlParameterColumnResolver {

  private static final Pattern INSERT_COLUMNS =
      Pattern.compile(
          "(?is)insert\\s+into\\s+\\S+\\s*\\(([^)]*)\\)\\s*values", Pattern.CASE_INSENSITIVE);

  private static final Pattern COLUMN_BEFORE_PLACEHOLDER =
      Pattern.compile("([\\w\\.\"]+)\\s*(?:=|<>|!=|>=|<=|>|<|(?i:like|in))\\s*\\(?\\s*$");

  private SqlParameterColumnResolver() {}

  /**
   * Returns one entry per placeholder, in JDBC order; the value is the column name or {@code null}.
   */
  public static List<String> resolve(String sql) {
    if (sql == null || sql.indexOf('?') < 0) {
      return List.of();
    }
    try {
      List<Integer> placeholderOffsets = placeholderOffsets(sql);
      String trimmed = sql.trim();
      if (trimmed.regionMatches(true, 0, "insert", 0, 6)) {
        List<String> columns = insertColumns(sql);
        if (columns.size() == placeholderOffsets.size()) {
          return columns;
        }
      }
      List<String> result = new ArrayList<>(placeholderOffsets.size());
      for (int offset : placeholderOffsets) {
        result.add(columnBefore(sql, offset));
      }
      return result;
    } catch (RuntimeException e) {
      // Resolution is a best-effort enhancement; never let it break logging.
      return List.of();
    }
  }

  private static List<Integer> placeholderOffsets(String sql) {
    // Escaped quotes ('' and "") need no special case: they come in pairs, so each adds two toggles
    // and the in-literal parity at every '?' stays correct (covered by tests).
    List<Integer> offsets = new ArrayList<>();
    boolean inSingle = false;
    boolean inDouble = false;
    for (int i = 0; i < sql.length(); i++) {
      char c = sql.charAt(i);
      if (c == '\'' && !inDouble) {
        inSingle = !inSingle;
      } else if (c == '"' && !inSingle) {
        inDouble = !inDouble;
      } else if (c == '?' && !inSingle && !inDouble) {
        offsets.add(i);
      }
    }
    return offsets;
  }

  private static List<String> insertColumns(String sql) {
    Matcher m = INSERT_COLUMNS.matcher(sql);
    if (!m.find()) {
      return List.of();
    }
    return Arrays.stream(m.group(1).split(","))
        .map(SqlParameterColumnResolver::cleanColumn)
        .toList();
  }

  private static String columnBefore(String sql, int placeholderOffset) {
    Matcher m = COLUMN_BEFORE_PLACEHOLDER.matcher(sql.substring(0, placeholderOffset));
    return m.find() ? cleanColumn(m.group(1)) : null;
  }

  private static String cleanColumn(String raw) {
    String c = raw.trim().replace("\"", "");
    int dot = c.lastIndexOf('.');
    return dot >= 0 ? c.substring(dot + 1) : c;
  }
}
