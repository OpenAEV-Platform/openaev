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
                  + ", which is not an active marked table");
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
          "marking active-tables have no "
              + MarkedTable.MARKING_COLUMN
              + " column: "
              + unknown
              + " (a table derived from a marked one is filtered with it and is not listed)");
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
   * Adds the tables derived from the active ones (see {@link MarkingDerivedTables}). Applied after
   * {@link #restrictTo}: a derived table is filtered exactly when the marked table its chain ends
   * on is active, and left unfiltered otherwise, so activating {@code assets} is the one switch for
   * everything derived from it.
   *
   * @throws IllegalArgumentException when a derived table is not linked, is listed twice, or is
   *     also active through a marking column of its own
   */
  public MarkedTables withDerived(Collection<MarkedTable> derived) {
    Map<String, MarkedTable> merged = new LinkedHashMap<>(byTable);
    Map<String, MarkedTable> pending = new LinkedHashMap<>();
    for (MarkedTable table : derived) {
      if (!table.isLinked()) {
        throw new IllegalArgumentException(table.table() + " is not a derived marked table");
      }
      if (merged.containsKey(table.table()) || pending.putIfAbsent(table.table(), table) != null) {
        throw new IllegalArgumentException(
            table.table()
                + " is already marked (own column or listed twice); it cannot be derived");
      }
    }
    // A derived table may hang off another derived one, so keep adding until a pass adds nothing;
    // what is left points, through its chain, to a marked table that is not active.
    boolean added = true;
    while (added) {
      added = false;
      for (var it = pending.values().iterator(); it.hasNext(); ) {
        MarkedTable table = it.next();
        if (merged.containsKey(table.linkedTable())) {
          merged.put(table.table(), table);
          it.remove();
          added = true;
        }
      }
    }
    return new MarkedTables(merged);
  }
}
