package io.openaev.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser-independent detector of unguarded tenant-table reads in (rewritten) SQL. It works purely
 * on text so it cannot share a blind spot with the JSQLParser-based rewriter it checks: a tenant
 * table whose {@code FROM}/{@code JOIN} reference the rewriter failed to wrap is caught here rather
 * than leaking silently.
 *
 * <p>Invariant checked per table: in the SQL, every {@code FROM}/{@code JOIN} to a tenant table
 * must be a {@code can_access_tenant} wrapper of the exact shape the rewriter produces ({@code FROM
 * <table> <alias> WHERE can_access_tenant(...)}). The number of references must equal the number of
 * wrappers, so a single unwrapped reference is caught even when sibling tables in the same
 * statement are wrapped.
 */
final class TenantSqlLeakOracle {

  private final Map<String, TablePatterns> byTable;

  TenantSqlLeakOracle(Collection<String> tenantTables) {
    Map<String, TablePatterns> map = new LinkedHashMap<>();
    tenantTables.stream().sorted().forEach(table -> map.put(table, TablePatterns.forTable(table)));
    this.byTable = map;
  }

  /** Tenant tables whose name appears as a word in the SQL (string literals removed first). */
  Set<String> mentioned(String sql) {
    String text = normalize(sql);
    Set<String> found = new LinkedHashSet<>();
    byTable.forEach(
        (table, patterns) -> {
          if (patterns.mention().matcher(text).find()) {
            found.add(table);
          }
        });
    return found;
  }

  /** Tenant tables with at least one {@code FROM}/{@code JOIN} reference that is not a wrapper. */
  List<String> unwrappedTenantTables(String sql) {
    String text = normalize(sql);
    List<String> unwrapped = new ArrayList<>();
    byTable.forEach(
        (table, patterns) -> {
          if (count(text, patterns.fromJoin()) != count(text, patterns.wrapper())) {
            unwrapped.add(table);
          }
        });
    return unwrapped;
  }

  /** Removes string literals and collapses whitespace so the textual matchers are reliable. */
  static String normalize(String sql) {
    return stripStringLiterals(sql).replaceAll("\\s+", " ");
  }

  /** Replaces single-quoted literals with {@code ''} so a table name inside text is not counted. */
  static String stripStringLiterals(String sql) {
    return sql.replaceAll("'(?:[^']|'')*'", "''");
  }

  static int count(String haystack, Pattern pattern) {
    Matcher matcher = pattern.matcher(haystack);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  /**
   * Textual matchers for one tenant table, all independent of the SQL parser: {@code mention}
   * detects the table name as a word; {@code fromJoin} matches a readable {@code FROM}/{@code JOIN}
   * to it; {@code wrapper} matches the {@code can_access_tenant} wrapper the rewriter must produce.
   * The trailing look-ahead keeps a shorter name from matching a longer one (e.g. {@code assets}
   * must not match {@code assets_archive}); the {@code (?<!delete )} look-behind excludes a DELETE
   * target, which is guarded by a WHERE predicate, not by a wrapper. Whitespace is normalized to
   * single spaces before matching, so the fixed-length look-behind is reliable.
   */
  record TablePatterns(Pattern mention, Pattern fromJoin, Pattern wrapper) {
    static TablePatterns forTable(String table) {
      String name = Pattern.quote(table);
      return new TablePatterns(
          Pattern.compile("(?i)(?<![a-z0-9_])" + name + "(?![a-z0-9_])"),
          Pattern.compile("(?i)(?<!delete )\\b(?:from|join)\\s+\"?" + name + "\"?(?![a-z0-9_])"),
          Pattern.compile(
              "(?i)\\bfrom\\s+\"?" + name + "\"?\\s+\\S+\\s+where\\s+can_access_tenant\\("));
    }
  }
}
