package io.openaev.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tables filtered by the marking dimension, indexed by table name (matched case-insensitively).
 * Mirrors {@link TenantTables}, with a {@link MarkedTable} instead of a family because a marking is
 * a set of ids rather than a single value.
 */
public record MarkedTables(Map<String, MarkedTable> byTable) {

  public static final MarkedTables EMPTY = new MarkedTables(Map.of());

  public MarkedTables {
    Map<String, MarkedTable> normalized = new LinkedHashMap<>();
    byTable.forEach((name, marked) -> normalized.put(name.toLowerCase(Locale.ROOT), marked));
    byTable = Map.copyOf(normalized);
    requireResolvableParents(byTable);
  }

  /**
   * A table marked through a parent is only as protected as that parent: the predicate reads the
   * parent's own marking. Every chain must therefore end on a table with a marking column of its
   * own, never on a missing table (a typo, or a parent left off the activation allowlist, which
   * would otherwise leave the child unprotected without any sign) and never loop.
   */
  private static void requireResolvableParents(Map<String, MarkedTable> byTable) {
    for (MarkedTable start : byTable.values()) {
      Set<String> visited = new HashSet<>();
      MarkedTable current = start;
      while (current.isLinked()) {
        if (!visited.add(current.table())) {
          throw new IllegalArgumentException(
              "marking links form a cycle through "
                  + current.table()
                  + " (from "
                  + start.table()
                  + ")");
        }
        String parentName = current.linkedTable();
        MarkedTable parent = byTable.get(parentName);
        if (parent == null) {
          throw new IllegalArgumentException(
              current.table()
                  + " is marked through "
                  + parentName
                  + ", which is not a marked table (is it missing from openaev.marking.active-tables?)");
        }
        current = parent;
      }
    }
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
   * table fails fast, to surface a typo (or a missing marking column) at startup rather than
   * silently leave a table unprotected.
   */
  public MarkedTables restrictTo(Collection<String> allowlist) {
    Set<String> allowed = new HashSet<>();
    allowlist.forEach(name -> allowed.add(name.toLowerCase(Locale.ROOT)));
    Set<String> unknown = new HashSet<>(allowed);
    unknown.removeAll(byTable.keySet());
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "marking active-tables have no " + MarkedTable.MARKING_COLUMN + " column: " + unknown);
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

  /**
   * Adds tables that are marked through a parent row instead of a column of their own. Applied
   * after {@link #restrictTo}, so a parent must be among the activated tables: linking a child to
   * an inactive parent would silently leave it unfiltered, and is refused instead.
   *
   * @throws IllegalArgumentException when a linked table already has its own marking column, is
   *     listed twice, or points to a parent that is not marked
   */
  public MarkedTables withLinked(Collection<MarkedTable> linked) {
    Map<String, MarkedTable> merged = new LinkedHashMap<>(byTable);
    for (MarkedTable link : linked) {
      if (!link.isLinked()) {
        throw new IllegalArgumentException(link.table() + " is not a linked marked table");
      }
      if (merged.putIfAbsent(link.table(), link) != null) {
        throw new IllegalArgumentException(
            link.table() + " is already marked (own column or listed twice); it cannot be linked");
      }
    }
    return new MarkedTables(merged);
  }
}
