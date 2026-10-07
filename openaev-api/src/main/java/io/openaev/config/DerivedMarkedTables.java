package io.openaev.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tables filtered by the derived marking dimension, indexed by table name. Mirrors {@link
 * MarkedTables}, with one difference: a derived table cannot be discovered from the schema (it has
 * no marking column), so the full set is the hand-written {@link #REGISTRY} and configuration only
 * chooses which entries are active.
 */
public record DerivedMarkedTables(Map<String, DerivedMarkedTable> byTable) {

  public static final DerivedMarkedTables EMPTY = new DerivedMarkedTables(Map.of());

  /**
   * Every table the derived dimension knows how to filter.
   *
   * <p>{@code injects} is guarded to root injects (Atomic Testing): an inject that belongs to a
   * scenario or a simulation is left visible until {@code scenarios} and {@code exercises} are
   * registered too, otherwise a visible scenario would show with some of its injects missing.
   *
   * <p>{@code findings} uses the full rule on every finding, whatever its inject: hidden when its
   * inject holds a restricted asset or when one of its own assets is restricted.
   */
  public static final DerivedMarkedTables REGISTRY =
      of(
          new DerivedMarkedTable(
              "injects",
              "%1$s.inject_scenario IS NOT NULL OR %1$s.inject_exercise IS NOT NULL"
                  + " OR can_see_inject(%1$s.inject_id)",
              List.of("can_see_inject", "can_see_asset_group")),
          new DerivedMarkedTable(
              "findings",
              "can_see_finding(%1$s.finding_id, %1$s.finding_inject_id)",
              List.of("can_see_finding", "can_see_inject", "can_see_asset_group")));

  public DerivedMarkedTables {
    Map<String, DerivedMarkedTable> normalized = new LinkedHashMap<>();
    byTable.forEach((name, derived) -> normalized.put(name.toLowerCase(Locale.ROOT), derived));
    byTable = Map.copyOf(normalized);
  }

  public static DerivedMarkedTables of(DerivedMarkedTable... tables) {
    Map<String, DerivedMarkedTable> byTable = new LinkedHashMap<>();
    for (DerivedMarkedTable table : tables) {
      byTable.put(table.table(), table);
    }
    return new DerivedMarkedTables(byTable);
  }

  /** Strips the surrounding double quotes an SQL dialect may put around an identifier. */
  private static String unquote(String name) {
    if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
      return name.substring(1, name.length() - 1);
    }
    return name;
  }

  /** The derived-marking metadata of a table, or null when the table is not derived-marked. */
  public DerivedMarkedTable get(String table) {
    return byTable.get(unquote(table).toLowerCase(Locale.ROOT));
  }

  public Set<String> tableNames() {
    return byTable.keySet();
  }

  /** Every SQL function the active tables' predicates call. */
  public Set<String> functions() {
    Set<String> functions = new HashSet<>();
    byTable.values().forEach(table -> functions.addAll(table.functions()));
    return functions;
  }

  /**
   * Restricts these tables to an activation allowlist. An empty allowlist activates nothing, so the
   * dimension stays inert. An entry that is not a registered derived table fails fast, to surface a
   * typo at startup rather than silently leave a table unprotected.
   */
  public DerivedMarkedTables restrictTo(Collection<String> allowlist) {
    Set<String> allowed = new HashSet<>();
    allowlist.forEach(name -> allowed.add(name.toLowerCase(Locale.ROOT)));
    Set<String> unknown = new HashSet<>(allowed);
    unknown.removeAll(byTable.keySet());
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "marking derived-tables are not registered derived tables: " + unknown);
    }
    Map<String, DerivedMarkedTable> kept = new LinkedHashMap<>();
    byTable.forEach(
        (name, derived) -> {
          if (allowed.contains(name)) {
            kept.put(name, derived);
          }
        });
    return new DerivedMarkedTables(kept);
  }
}
