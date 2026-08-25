package io.openaev.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tables filtered by the marking dimension, indexed by table name (matched case-insensitively).
 * Mirrors {@link TenantTables}, with a {@link MarkedTable} instead of a family because a marking
 * lives in a join table rather than in a column.
 */
public record MarkedTables(Map<String, MarkedTable> byTable) {

  public static final MarkedTables EMPTY = new MarkedTables(Map.of());

  public MarkedTables {
    Map<String, MarkedTable> normalized = new LinkedHashMap<>();
    byTable.forEach((name, marked) -> normalized.put(name.toLowerCase(Locale.ROOT), marked));
    byTable = Map.copyOf(normalized);
  }

  /** Strips the surrounding double quotes an SQL dialect may put around an identifier. */
  private static String unquote(String name) {
    if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
      return name.substring(1, name.length() - 1);
    }
    return name;
  }

  /** The marking metadata of a table, or null when the table is not marked. */
  public MarkedTable get(String table) {
    return byTable.get(unquote(table).toLowerCase(Locale.ROOT));
  }

  public Set<String> tableNames() {
    return byTable.keySet();
  }

  /**
   * Restricts these tables to an activation allowlist, the table-by-table rollout knob. An empty
   * allowlist activates nothing, so the dimension stays inert. An entry that is not a known marked
   * table fails fast, to surface a typo (or a missing join table) at startup rather than silently
   * leave a table unprotected.
   */
  public MarkedTables restrictTo(Collection<String> allowlist) {
    Set<String> allowed = new HashSet<>();
    allowlist.forEach(name -> allowed.add(name.toLowerCase(Locale.ROOT)));
    Set<String> unknown = new HashSet<>(allowed);
    unknown.removeAll(byTable.keySet());
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "marking active-tables have no "
              + MarkedTable.JOIN_TABLE_SUFFIX
              + " join table: "
              + unknown);
    }
    Map<String, MarkedTable> kept = new LinkedHashMap<>();
    byTable.forEach(
        (name, marked) -> {
          if (allowed.contains(name)) {
            kept.put(name, marked);
          }
        });
    return new MarkedTables(kept);
  }
}
