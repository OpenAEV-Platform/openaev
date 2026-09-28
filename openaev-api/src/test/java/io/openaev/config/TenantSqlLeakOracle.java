package io.openaev.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser-independent detector of unguarded tenant-table reads in (rewritten) SQL. It works purely
 * on text so it cannot share a blind spot with the JSQLParser-based rewriter it checks: a tenant
 * table whose {@code FROM}/{@code JOIN} reference the rewriter failed to guard is caught here
 * rather than leaking silently.
 *
 * <p>Invariant checked per table: every {@code FROM}/{@code JOIN} reference to a tenant table must
 * be guarded by a {@code can_access_tenant} predicate on that reference's own {@code tenant_id}.
 * The rewriter produces two guarded shapes, both covered here by binding the guard to the
 * reference's alias: a joined (or sub-selected) table is wrapped in a filtered sub-query ({@code
 * FROM <table> <alias> WHERE can_access_tenant(<alias>.tenant_id)}), and the primary FROM item is
 * narrowed by moving that same predicate into the select's own WHERE ({@code FROM <table> <alias>
 * ... WHERE ... can_access_tenant(<alias>.tenant_id)}). A single reference whose alias carries no
 * such predicate is caught even when sibling references in the same statement are guarded. A DELETE
 * target ({@code DELETE FROM <table>}) is guarded by a WHERE predicate, not a reference the
 * rewriter wraps, so it is excluded from the reference scan below.
 */
final class TenantSqlLeakOracle {

  /**
   * Tokens that can follow {@code FROM <table>} but are not an alias: keywords and clause starts.
   * When one of them (or nothing) follows the table name, the reference is aliasless and the
   * rewriter guards it on the table name itself, which is what {@link TablePatterns#referenceName}
   * falls back to.
   */
  private static final Set<String> NOT_AN_ALIAS =
      Set.of(
          "where",
          "group",
          "order",
          "on",
          "and",
          "or",
          "left",
          "right",
          "inner",
          "outer",
          "full",
          "cross",
          "join",
          "union",
          "limit",
          "having",
          "offset",
          "natural",
          "using",
          "for",
          "window",
          "fetch",
          "returning");

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

  /** Tenant tables with at least one {@code FROM}/{@code JOIN} reference that is not guarded. */
  List<String> unwrappedTenantTables(String sql) {
    String text = normalize(sql);
    List<String> unwrapped = new ArrayList<>();
    byTable.forEach(
        (table, patterns) -> {
          if (patterns.hasUnguardedReference(text)) {
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

  /**
   * Textual matchers for one tenant table, all independent of the SQL parser: {@code mention}
   * detects the table name as a word; {@code reference} matches a readable {@code FROM}/{@code
   * JOIN} to it and captures the alias that follows (if any). The trailing look-ahead keeps a
   * shorter name from matching a longer one (e.g. {@code assets} must not match {@code
   * assets_archive}); the {@code (?<!delete )} look-behind excludes a DELETE target, which is
   * guarded by a WHERE predicate, not by a reference the rewriter wraps. Whitespace is normalized
   * to single spaces before matching, so the fixed-length look-behind is reliable.
   */
  record TablePatterns(String table, Pattern mention, Pattern reference) {
    static TablePatterns forTable(String table) {
      String name = Pattern.quote(table);
      return new TablePatterns(
          table,
          Pattern.compile("(?i)(?<![a-z0-9_])" + name + "(?![a-z0-9_])"),
          Pattern.compile(
              "(?i)(?<!delete )\\b(?:from|join)\\s+\"?"
                  + name
                  + "\"?(?![a-z0-9_])(?:\\s+(?:as\\s+)?(\"?[a-z0-9_]+\"?))?"));
    }

    /**
     * Whether any {@code FROM}/{@code JOIN} reference to this table lacks a {@code
     * can_access_tenant} predicate on its own reference (alias, or the table name when aliasless).
     * The wrapper form and the narrowed-primary form both satisfy this, since both emit that
     * predicate on the reference.
     */
    boolean hasUnguardedReference(String text) {
      Matcher matcher = reference.matcher(text);
      while (matcher.find()) {
        String ref = referenceName(matcher.group(1));
        Pattern guard =
            Pattern.compile("(?i)can_access_tenant\\(\\s*" + Pattern.quote(ref) + "\\.tenant_id");
        // Search the reference's own scope, not the whole statement. Aliases repeat constantly
        // across
        // nested selects, so a guarded "documents d" in one sub-query would otherwise launder an
        // unguarded "documents d" in another. Both guard shapes live in the same scope as their
        // FROM:
        // the wrapper emits the predicate inside its parentheses, the narrowed primary in that
        // select's own WHERE.
        if (!guard.matcher(scopeFrom(text, matcher.end())).find()) {
          return true;
        }
      }
      return false;
    }

    /**
     * The text from a reference to the end of the parenthesis group enclosing it, or to the end of
     * the statement when the reference sits at the top level. Quoted string literals are already
     * blanked before the oracle runs, so a parenthesis inside a literal cannot skew the depth
     * count.
     */
    private static String scopeFrom(String text, int start) {
      int depth = 0;
      for (int i = start; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '(') {
          depth++;
        } else if (c == ')') {
          if (depth == 0) {
            return text.substring(start, i);
          }
          depth--;
        }
      }
      return text.substring(start);
    }

    /** The reference the guard predicate must name: the alias, or the table name when aliasless. */
    private String referenceName(String aliasToken) {
      if (aliasToken == null) {
        return table;
      }
      String alias = aliasToken.replace("\"", "");
      return NOT_AN_ALIAS.contains(alias.toLowerCase(Locale.ROOT)) ? table : alias;
    }
  }
}
